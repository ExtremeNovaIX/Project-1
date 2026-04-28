package p1.service.archive.recentwindow;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import p1.config.prop.AssistantProperties;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 将召回结果聚合为候选目标，并选出最终 winner。
 * <p>
 * 术语说明：
 * - archive：单个记忆事件节点，是向量召回返回的最小命中单位。
 * - group：一组 archive 的集合，是 recent-window 连接优先考虑的目标单位。
 * - match：某个 query archive 在向量库中召回到的一条 archive 命中。
 * - candidate：参与排序的候选目标。通常代表一个目标 group；
 *   如果命中 archive 没有 groupId，则退化为单个 archive 候选。
 * - anchor：candidate 内最终用于建立 archive link 的具体 archive。
 * <p>
 * 整体流程：
 * 1. recall 阶段返回 archive 级 match；
 * 2. ranker 按 groupId 将 match 聚合为 candidate；
 * 3. candidate 之间排序选出 winner；
 * 4. winner 内再选择一个 anchor archive 作为实际落边目标。
 */
@Service
@RequiredArgsConstructor
class RecentWindowRanker {

    private final AssistantProperties props;

    RankResult rank(@NonNull List<RecallResult> recallResults, @NonNull RankContext context) {
        Map<String, CandidateAggregate> candidates = new LinkedHashMap<>();

        for (RecallResult recallResult : recallResults) {
            if (recallResult == null || recallResult.skipped()) {
                continue;
            }

            for (var match : recallResult.usableMatches()) {
                Long targetArchiveId = match.archive().getId();

                // 优先按 groupId 聚合；只有缺 groupId 时才退化为单 archive 候选。
                // 这样同一目标组内的多个命中会合并为一个候选，避免目标组内 archive 彼此竞争。
                String candidateKey = RecentWindowSupport.candidateKey(match.groupId(), targetArchiveId);
                // 相同id的聚合为同一个CandidateAggregate进行处理
                CandidateAggregate candidate = candidates.computeIfAbsent(
                        candidateKey,
                        ignored -> new CandidateAggregate(
                                RecentWindowSupport.normalize(match.groupId()),
                                targetArchiveId,
                                match.archive().getCreatedAt()
                        )
                );
                // 将当前 match 吸收到候选中，更新该候选的向量支持分、anchor 候选和命中次数。
                candidate.addMatch(recallResult.queryIndex(), match, recallResult.weight());
            }
        }

        // 对每个候选组进行打分并且挑选出每个组中分数最高的候选archive
        candidates.values().forEach(candidate -> scoreCandidate(candidate, context));
        // 按最终排序分数对候选排序，排名第一的 candidate 将作为 winner。
        List<CandidateAggregate> rankedCandidates = candidates.values().stream()
                .sorted(rankComparator())
                .toList();

        return new RankResult(
                rankedCandidates.stream().map(CandidateAggregate::toTrace).toList(),
                rankedCandidates.isEmpty() ? null : rankedCandidates.getFirst().toTarget()
        );
    }

    /**
     * 对候选执行重排阶段处理。
     * 打分顺序固定为：时间衰减 -> 标签 boost -> anchor 选择。
     */
    private void scoreCandidate(@NonNull CandidateAggregate candidate, @NonNull RankContext context) {
        // 计算时间衰减因子并应用到candidate上
        TimeDecay decay = computeDecay(context, candidate.groupId(), candidate.createdAt());
        candidate.applyDecay(decay);

        // 计算命中的标签boost并应用到candidate上
        TagBoost boost = computeIdfBoost(context, candidate.groupId());
        candidate.applyBoost(props.getEventTree().getRecentWindowIdfBoostWeight(), boost);
        // 在候选目标内选择最终用于建立 archive link 的锚点 archive。
        candidate.chooseAnchor(boost.sharedTags());
    }

    private Comparator<CandidateAggregate> rankComparator() {
        return Comparator.comparingDouble(CandidateAggregate::boostedSupportScore)
                .thenComparingDouble(CandidateAggregate::bestScore)
                .thenComparingInt(CandidateAggregate::hitCount)
                .reversed();
    }

    private TagBoost computeIdfBoost(RankContext context, String groupId) {
        if (context.currentGroupTags().isEmpty() || context.taggedGroupCount() <= 0) {
            return TagBoost.none();
        }

        Set<String> candidateTags = context.tagsByGroupId().get(RecentWindowSupport.normalize(groupId));
        if (candidateTags == null || candidateTags.isEmpty()) {
            return TagBoost.none();
        }

        Set<String> sharedTags = new LinkedHashSet<>(context.currentGroupTags());
        sharedTags.retainAll(candidateTags);
        if (sharedTags.size() < props.getEventTree().getRecentWindowIdfMinSharedTags()) {
            return TagBoost.none();
        }

        double rawIdfScore = 0.0;
        for (String tag : sharedTags) {
            int df = context.documentFrequency().getOrDefault(tag, 0);
            double numerator = context.taggedGroupCount() + props.getEventTree().getRecentWindowIdfDocCountSmoothing();
            double denominator = df + props.getEventTree().getRecentWindowIdfDfSmoothing();
            if (numerator <= 0.0 || denominator <= 0.0) {
                continue;
            }

            // 这里累加的是共享标签的组级 IDF，不是单条 match 的分数。
            rawIdfScore += Math.max(0.0, Math.log(numerator / denominator));
        }

        if (rawIdfScore <= 0.0) {
            return new TagBoost(0.0, List.copyOf(sharedTags));
        }

        double normalizationScale = Math.max(1.0e-6, props.getEventTree().getRecentWindowIdfNormalizationScale());
        double normalizedScore = 1.0 - Math.exp(-rawIdfScore / normalizationScale);
        return new TagBoost(clamp01(normalizedScore), List.copyOf(sharedTags));
    }

    private TimeDecay computeDecay(RankContext context, String groupId, LocalDateTime fallbackCreatedAt) {
        LocalDateTime createdAt = resolveCreatedAt(context, groupId, fallbackCreatedAt);
        if (createdAt == null) {
            return TimeDecay.none();
        }

        double ageHours = Math.max(0.0, Duration.between(createdAt, context.referenceTime()).toSeconds() / 3600.0);
        double coefficient = Math.max(0.0, props.getEventTree().getRecentWindowTimeDecayCoefficient());
        if (coefficient <= 0.0) {
            return new TimeDecay(1.0, ageHours);
        }

        double windowHours = Math.max(1.0, props.getEventTree().getRecentWindowHours());
        double normalizedAge = ageHours / windowHours;
        double factor = Math.exp(-coefficient * normalizedAge);
        return new TimeDecay(clamp01(factor), ageHours);
    }

    private LocalDateTime resolveCreatedAt(RankContext context, String groupId, LocalDateTime fallbackCreatedAt) {
        String normalizedGroupId = RecentWindowSupport.normalize(groupId);
        if (!normalizedGroupId.isBlank()) {
            LocalDateTime groupCreatedAt = context.createdAtByGroupId().get(normalizedGroupId);
            if (groupCreatedAt != null) {
                return groupCreatedAt;
            }
        }
        return fallbackCreatedAt;
    }

    private double clamp01(double value) {
        if (value <= 0.0) {
            return 0.0;
        }
        return Math.min(value, 1.0);
    }
}
