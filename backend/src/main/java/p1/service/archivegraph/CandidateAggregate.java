package p1.service.archivegraph;

import p1.service.ArchiveEmbeddingService;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 单个 candidate 的聚合状态。
 * <p>
 * candidate 是排序单位，通常对应一个目标 group；
 * anchor 是落边单位，对应 group 内某个具体 archive。
 * <p>
 * 该对象负责吸收多个 archive 级 match，
 * 并维护 candidate 排序和 anchor 选择所需的中间状态。
 * <p>
 * 关键状态：
 * - groupId：候选目标组 ID，可能为空
 * - targetArchiveId：当前候选的默认落边 archive，后续可能被 chooseAnchor() 改写
 * - vectorSupportScore：该候选的主向量分，取各 query 最佳加权命中的最大值
 * - bestScorePerQuery：每个 query 对该候选的最佳加权命中，用于避免同一 query 重复刷分
 * - anchors：候选 group 内可用于最终落边的 archive 候选
 * - bestScore：当前候选内最高的原始向量分
 * - hitCount：该候选累计吸收的 match 数
 */
final class CandidateAggregate {

    private final String groupId;
    private Long targetArchiveId;
    private double vectorSupportScore;
    private double timeAdjustedSupportScore;
    private double boostedSupportScore;
    private double bestScore;
    private double timeDecayFactor = 1.0;
    private double normalizedIdfScore;
    private Double candidateAgeHours;
    private LocalDateTime createdAt;
    private int hitCount;
    private List<String> sharedTags = List.of();
    private final Map<Integer, Double> bestScorePerQuery = new HashMap<>();
    private final Map<Long, AnchorCandidate> anchors = new LinkedHashMap<>();
    private List<String> anchorMatchedTags = List.of();

    CandidateAggregate(String groupId, Long targetArchiveId, LocalDateTime createdAt) {
        this.groupId = RecentWindowSupport.normalize(groupId);
        this.targetArchiveId = targetArchiveId;
        this.createdAt = createdAt;
    }

    /**
     * 吸收一条命中结果并更新候选聚合状态。
     */
    void addMatch(int queryIndex, ArchiveEmbeddingService.ArchiveVectorMatch match, double weight) {
        // 把原始向量分数乘上 query 权重，得到加权向量分数，越靠近根节点的 archive 权重越高。
        double weightedScore = match.score() * weight;
        // 同一个 query，只使用merge保留最强命中
        bestScorePerQuery.merge(queryIndex, weightedScore, Math::max);

        // 同一个 query 对同一候选只保留最高加权分，避免同一 query 多个命中刷分。
        vectorSupportScore = bestScorePerQuery.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        // 如果多个 query 都命中了同一个 archive，使用merge只保留最强命中
        if (match.archive() != null && match.archive().getId() != null) {
            AnchorCandidate anchor = AnchorCandidate.fromMatch(match, RecentWindowSupport.searchableArchiveText(match.archive()));
            anchors.merge(anchor.archiveId(), anchor, AnchorCandidate::merge);
        }

        if (match.score() > bestScore) {
            bestScore = match.score();
            targetArchiveId = match.archive().getId();
        }
        if (createdAt == null && match.archive() != null) {
            createdAt = match.archive().getCreatedAt();
        }
        hitCount++;
    }

    void applyDecay(TimeDecay decay) {
        timeDecayFactor = decay.factor();
        candidateAgeHours = decay.ageHours();
        timeAdjustedSupportScore = vectorSupportScore * timeDecayFactor;
        boostedSupportScore = timeAdjustedSupportScore;
    }

    void applyBoost(double boostWeight, TagBoost boost) {
        sharedTags = boost.sharedTags();
        normalizedIdfScore = boost.normalizedScore();
        boostedSupportScore = timeAdjustedSupportScore * (1.0 + boostWeight * normalizedIdfScore);
    }

    /**
     * 在当前候选 group 内选择最终用于建立 archive link 的锚点 archive。
     * <p>
     * recent-window 排序先以 group 为单位进行，
     * 多条 match 会先按 groupId 聚合成 CandidateAggregate。
     * <p>
     * 当 candidate group 胜出后，
     * 仍需要从该 group 内多个命中的 archive 中，
     * 选择一个最合适的节点作为真正的落边目标。
     * <p>
     * 选择优先级：
     * 1. 命中的共享标签数量（越多越优先）
     * 2. 原始向量分数（bestScore）
     * <p>
     * 最终会更新：
     * - targetArchiveId
     * - bestScore
     * - anchorMatchedTags
     */
    void chooseAnchor(List<String> effectiveSharedTags) {
        AnchorCandidate selected = anchors.values().stream()
                .max(Comparator.comparingInt((AnchorCandidate anchor) -> anchor.matchedTagCount(effectiveSharedTags))
                        .thenComparingDouble(AnchorCandidate::bestScore))
                .orElse(null);
        if (selected == null) {
            return;
        }

        targetArchiveId = selected.archiveId();
        bestScore = selected.bestScore();
        anchorMatchedTags = selected.matchedTags(effectiveSharedTags);
    }

    String groupId() {
        return groupId;
    }

    LocalDateTime createdAt() {
        return createdAt;
    }

    double boostedSupportScore() {
        return boostedSupportScore;
    }

    double bestScore() {
        return bestScore;
    }

    int hitCount() {
        return hitCount;
    }

    LinkTarget toTarget() {
        return new LinkTarget(
                groupId,
                targetArchiveId,
                vectorSupportScore,
                timeAdjustedSupportScore,
                timeDecayFactor,
                candidateAgeHours,
                boostedSupportScore,
                bestScore,
                normalizedIdfScore,
                List.copyOf(sharedTags),
                List.copyOf(anchorMatchedTags),
                hitCount
        );
    }

    CandidateTrace toTrace() {
        return new CandidateTrace(
                groupId,
                targetArchiveId,
                vectorSupportScore,
                timeAdjustedSupportScore,
                timeDecayFactor,
                candidateAgeHours,
                boostedSupportScore,
                bestScore,
                normalizedIdfScore,
                hitCount,
                List.copyOf(sharedTags),
                List.copyOf(anchorMatchedTags)
        );
    }
}
