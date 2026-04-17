package p1.service.archivegraph;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import p1.config.prop.AssistantProperties;
import p1.infrastructure.vector.ArchiveVectorLibrary;
import p1.model.document.MemoryArchiveDocument;
import p1.service.ArchiveEmbeddingService;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行 recent-24h 向量召回，并过滤掉不可用命中。
 */
@Service
@RequiredArgsConstructor
class RecentWindowRecall {

    private static final int MAX_QUERY_ARCHIVES = 3;
    private static final int SEARCH_TOP_K = 10;

    private final AssistantProperties props;
    private final ArchiveEmbeddingService archiveEmbeddingService;

    /**
     * 使用当前组前MAX_QUERY_ARCHIVES个 archive 的 keywordSummary 进行召回
     * 1. 每个 archive 的 keywordSummary 作为召回 query。
     * 2. 每个 query 的召回结果，只保留最低 minScore 的 TopK 个。
     * 3. 进行可用性过滤，只保留可用的命中。
     */
    List<RecallResult> recall(String sessionId,
                              MemoryArchiveDocument rootArchive,
                              String currentGroupId,
                              List<MemoryArchiveDocument> group) {
        double minScore = props.getEventTree().getRecentWindowScoreLinkThreshold();
        List<RecallResult> results = new ArrayList<>();
        List<MemoryArchiveDocument> queries = selectQueries(group);

        for (int index = 0; index < queries.size(); index++) {
            MemoryArchiveDocument queryArchive = queries.get(index);
            String queryText = buildQuery(queryArchive);
            double weight = weightFor(index);
            if (queryText.isBlank()) {
                results.add(RecallResult.skipped(queryArchive, index, weight, "缺少 keywordSummary"));
                continue;
            }

            // 使用queryText在recent-24h向量库召回最低minScore的 TopK个命中
            List<ArchiveEmbeddingService.ArchiveVectorMatch> matches = archiveEmbeddingService.searchArchiveMatches(
                    sessionId,
                    ArchiveVectorLibrary.RECENT_24H,
                    queryText,
                    SEARCH_TOP_K,
                    minScore
            );

            // 进行可用性过滤
            List<ArchiveEmbeddingService.ArchiveVectorMatch> usableMatches = matches.stream()
                    .filter(match -> isUsableMatch(rootArchive, currentGroupId, match))
                    .toList();
            results.add(RecallResult.resolved(queryArchive, index, weight, queryText, usableMatches));
        }

        return List.copyOf(results);
    }

    /**
     * 当前策略只使用组内前几个 archive 作为 query。
     */
    private List<MemoryArchiveDocument> selectQueries(List<MemoryArchiveDocument> archives) {
        if (archives == null || archives.isEmpty()) {
            return List.of();
        }

        return archives.stream()
                .limit(MAX_QUERY_ARCHIVES)
                .toList();
    }

    /**
     * 计算当前节点的召回权重，越靠近跟节点权重越高。
     */
    private double weightFor(int index) {
        return 1.0 / (index + 1);
    }

    /**
     * 可用命中不能指回自己，也不能落回当前组。
     */
    private boolean isUsableMatch(MemoryArchiveDocument rootArchive,
                                  String currentGroupId,
                                  ArchiveEmbeddingService.ArchiveVectorMatch match) {
        if (match == null || match.archive() == null || match.archive().getId() == null) {
            return false;
        }

        if (rootArchive == null || rootArchive.getId() == null) {
            return false;
        }

        if (rootArchive.getId().equals(match.archive().getId())) {
            return false;
        }

        return !currentGroupId.equals(RecentWindowSupport.normalize(match.groupId()));
    }

    /**
     * 构建当前 query 文本，使用当前节点的 keywordSummary。
     */
    private String buildQuery(MemoryArchiveDocument archive) {
        return RecentWindowSupport.normalize(archive == null ? null : archive.getKeywordSummary());
    }
}
