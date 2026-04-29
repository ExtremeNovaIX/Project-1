package p1.service.archive.recentwindow;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import p1.model.document.MemoryArchiveDocument;

import java.util.List;

/**
 * recent-window 节点链接服务的入口。
 * 负责串起上下文构建、召回、重排和 trace 组装。
 */
@Service
@RequiredArgsConstructor
public class RecentWindowTargetSelector {

    private final RecentWindowContextBuilder contextBuilder;
    private final RecentWindowRecall recall;
    private final RecentWindowRanker ranker;

    /**
     * 为当前组 root 解析最佳 recent-window 库中的目标。
     */
    TargetSelectResult selectTarget(@NonNull String sessionId,
                                    @NonNull List<MemoryArchiveDocument> group,
                                    List<String> currentGroupTags) {
        MemoryArchiveDocument rootArchive = group.getFirst();
        String currentGroupId = RecentWindowSupport.normalize(rootArchive.getGroupId());
        RankContext context = contextBuilder.build(sessionId, rootArchive, currentGroupTags);
        ScoreTrace trace = new ScoreTrace(
                rootArchive.getId(),
                currentGroupId,
                List.copyOf(context.currentGroupTags()),
                context.taggedGroupCount()
        );

        // 在recent-24h 向量库中召回可用命中
        List<RecallResult> recallResults = recall.recall(sessionId, rootArchive, currentGroupId, group);

        // trace 留在 resolver 里写入，避免下游 helper 同时承担业务和观测职责。
        for (RecallResult recallResult : recallResults) {
            if (recallResult.skipped()) {
                trace.recordSkippedQuery(
                        recallResult.queryArchive(),
                        recallResult.queryIndex(),
                        recallResult.weight(),
                        recallResult.skippedReason()
                );
                continue;
            }

            trace.recordQuery(
                    recallResult.queryArchive(),
                    recallResult.queryIndex(),
                    recallResult.weight(),
                    recallResult.queryText(),
                    recallResult.usableMatches(),
                    RecentWindowSupport::normalize
            );
        }

        RankResult rankResult = ranker.rank(recallResults, context);
        trace.recordCandidates(rankResult.candidates());
        trace.recordWinner(rankResult.winner());
        return new TargetSelectResult(rankResult.winner(), trace);
    }
}
