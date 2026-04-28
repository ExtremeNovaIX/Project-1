package p1.service.archive.recentwindow;

import p1.model.document.MemoryArchiveDocument;
import p1.service.archive.ArchiveEmbeddingService;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 保存一次 recent-window 解析的完整 trace。
 */
final class ScoreTrace {

    private final Long rootArchiveId;
    private final String currentGroupId;
    private final List<String> currentGroupTags;
    private final int taggedGroupCount;
    private final List<QueryTrace> queries = new ArrayList<>();
    private final List<CandidateTrace> candidates = new ArrayList<>();
    private LinkTarget winner;
    private int usableMatchCount;

    ScoreTrace(Long rootArchiveId,
               String currentGroupId,
               List<String> currentGroupTags,
               int taggedGroupCount) {
        this.rootArchiveId = rootArchiveId;
        this.currentGroupId = currentGroupId;
        this.currentGroupTags = currentGroupTags == null ? List.of() : currentGroupTags;
        this.taggedGroupCount = taggedGroupCount;
    }

    QueryTrace recordSkippedQuery(MemoryArchiveDocument queryArchive,
                                  int queryIndex,
                                  double weight,
                                  String skippedReason) {
        QueryTrace queryTrace = new QueryTrace(
                queryIndex,
                queryArchive == null ? null : queryArchive.getId(),
                weight,
                "",
                skippedReason,
                List.of()
        );
        queries.add(queryTrace);
        return queryTrace;
    }

    QueryTrace recordQuery(MemoryArchiveDocument queryArchive,
                           int queryIndex,
                           double weight,
                           String queryText,
                           List<ArchiveEmbeddingService.ArchiveVectorMatch> usableMatches,
                           StringNormalizer normalizer) {
        List<MatchTrace> matchTraces = usableMatches.stream()
                .map(match -> new MatchTrace(
                        match.archive().getId(),
                        match.groupId(),
                        match.score(),
                        match.groupOrder(),
                        normalizer.normalize(match.archive().getKeywordSummary())
                ))
                .toList();
        usableMatchCount += matchTraces.size();

        QueryTrace queryTrace = new QueryTrace(
                queryIndex,
                queryArchive == null ? null : queryArchive.getId(),
                weight,
                queryText,
                null,
                matchTraces
        );
        queries.add(queryTrace);
        return queryTrace;
    }

    void recordCandidates(List<CandidateTrace> sortedCandidates) {
        candidates.clear();
        candidates.addAll(sortedCandidates);
    }

    void recordWinner(LinkTarget winner) {
        this.winner = winner;
    }

    Long rootArchiveId() {
        return rootArchiveId;
    }

    String currentGroupId() {
        return currentGroupId;
    }

    List<String> currentGroupTags() {
        return List.copyOf(currentGroupTags);
    }

    int currentTagCount() {
        return currentGroupTags.size();
    }

    int taggedGroupCount() {
        return taggedGroupCount;
    }

    int usableMatchCount() {
        return usableMatchCount;
    }

    int candidateCount() {
        return candidates.size();
    }

    List<QueryTrace> queries() {
        return List.copyOf(queries);
    }

    List<CandidateTrace> candidates() {
        return List.copyOf(candidates);
    }

    LinkTarget winner() {
        return winner;
    }

    @FunctionalInterface
    interface StringNormalizer {
        String normalize(String text);
    }
}

record QueryTrace(int queryIndex,
                  Long queryArchiveId,
                  double weight,
                  String queryText,
                  String skippedReason,
                  List<MatchTrace> matches) {
}

record MatchTrace(Long targetArchiveId,
                  String targetGroupId,
                  double score,
                  Integer groupOrder,
                  String keywordSummary) {
}

record CandidateTrace(String groupId,
                      Long targetArchiveId,
                      double vectorSupportScore,
                      double timeAdjustedSupportScore,
                      double timeDecayFactor,
                      Double candidateAgeHours,
                      double boostedSupportScore,
                      double bestScore,
                      double normalizedIdfScore,
                      int hitCount,
                      List<String> sharedTags,
                      List<String> anchorMatchedTags) {
}

record TagBoost(double normalizedScore, List<String> sharedTags) {
    static TagBoost none() {
        return new TagBoost(0.0, List.of());
    }
}

record TimeDecay(double factor, Double ageHours) {
    static TimeDecay none() {
        return new TimeDecay(1.0, null);
    }
}

record LinkTarget(String groupId,
                  Long targetArchiveId,
                  double vectorSupportScore,
                  double timeAdjustedSupportScore,
                  double timeDecayFactor,
                  Double candidateAgeHours,
                  double boostedSupportScore,
                  double bestScore,
                  double normalizedIdfScore,
                  List<String> sharedTags,
                  List<String> anchorMatchedTags,
                  int hitCount) {
    int sharedTagCount() {
        return sharedTags == null ? 0 : sharedTags.size();
    }
}

record TargetSelectResult(LinkTarget target, ScoreTrace trace) {
}

/**
 * 单个 query archive 的召回结果。
 *
 * @param queryArchive  发起召回的 archive 文档
 * @param queryIndex    archive 文档在查询组中的位置
 * @param weight        archive 文档的权重（越靠近根节点权重越高）
 * @param queryText     查询文本
 * @param skippedReason 跳过原因
 * @param usableMatches 可用的命中list
 */
record RecallResult(MemoryArchiveDocument queryArchive,
                    int queryIndex,
                    double weight,
                    String queryText,
                    String skippedReason,
                    List<ArchiveEmbeddingService.ArchiveVectorMatch> usableMatches
) {

    static RecallResult skipped(MemoryArchiveDocument queryArchive,
                                int queryIndex,
                                double weight,
                                String skippedReason) {
        return new RecallResult(
                queryArchive,
                queryIndex,
                weight,
                "",
                skippedReason,
                List.of()
        );
    }

    static RecallResult resolved(MemoryArchiveDocument queryArchive,
                                 int queryIndex,
                                 double weight,
                                 String queryText,
                                 List<ArchiveEmbeddingService.ArchiveVectorMatch> usableMatches) {
        return new RecallResult(
                queryArchive,
                queryIndex,
                weight,
                queryText,
                null,
                usableMatches == null ? List.of() : List.copyOf(usableMatches)
        );
    }

    boolean skipped() {
        return skippedReason != null;
    }
}

record RankResult(List<CandidateTrace> candidates, LinkTarget winner) {
}

/**
 * 单个候选 group 内的 archive 级锚点候选。
 * <p>
 * 系统先决定“连到哪个 group”，
 * 再决定“具体连到 group 里的哪个 archive”。
 * <p>
 * AnchorCandidate 就是第二阶段用于选择最终目标节点的候选对象。
 *
 * @param archiveId      候选 archive 的唯一标识，用于最终落边
 * @param bestScore      该 archive 在所有命中中的最高原始向量分数
 * @param groupOrder     archive 在目标 group 内的顺序位置，通常越靠前越接近 root
 * @param searchableText 用于标签匹配的归一化全文本（topic + keywordSummary + narrative）
 */
record AnchorCandidate(Long archiveId,
                       double bestScore,
                       Integer groupOrder,
                       String searchableText) {

    /**
     * 转换ArchiveVectorMatch为AnchorCandidate
     */
    static AnchorCandidate fromMatch(ArchiveEmbeddingService.ArchiveVectorMatch match, String searchableText) {
        return new AnchorCandidate(
                match.archive().getId(),
                match.score(),
                match.groupOrder(),
                searchableText
        );
    }

    /**
     * 取最高分数的候选对象
     */
    AnchorCandidate merge(AnchorCandidate other) {
        if (other == null) {
            return this;
        }
        if (other.bestScore() > bestScore) {
            return other;
        }
        return this;
    }

    int matchedTagCount(List<String> tags) {
        return matchedTags(tags).size();
    }

    List<String> matchedTags(List<String> tags) {
        if (tags == null || tags.isEmpty() || searchableText == null || searchableText.isBlank()) {
            return List.of();
        }

        List<String> matched = new ArrayList<>();
        for (String tag : tags) {
            String normalizedTag = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
            if (!normalizedTag.isBlank() && searchableText.contains(normalizedTag)) {
                matched.add(tag);
            }
        }
        return List.copyOf(matched);
    }
}

record RankContext(Set<String> currentGroupTags,
                   Map<String, Set<String>> tagsByGroupId,
                   Map<String, Integer> documentFrequency,
                   int taggedGroupCount,
                   Map<String, LocalDateTime> createdAtByGroupId,
                   LocalDateTime referenceTime) {
}
