package p1.service.archive.recentwindow;

import org.junit.jupiter.api.Test;
import p1.config.prop.AssistantProperties;
import p1.model.document.MemoryArchiveDocument;
import p1.service.archive.ArchiveEmbeddingService;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecentWindowRankerTest {

    @Test
    void shouldAggregateByGroupAndSelectAnchorUsingSharedTags() {
        AssistantProperties props = new AssistantProperties();
        props.getEventTree().setRecentWindowTimeDecayCoefficient(0.0);
        props.getEventTree().setRecentWindowIdfBoostWeight(0.35);
        props.getEventTree().setRecentWindowIdfMinSharedTags(1);
        props.getEventTree().setRecentWindowIdfDocCountSmoothing(1.0);
        props.getEventTree().setRecentWindowIdfDfSmoothing(1.0);
        props.getEventTree().setRecentWindowIdfNormalizationScale(2.0);

        RecentWindowRanker ranker = new RecentWindowRanker(props);
        LocalDateTime now = LocalDateTime.now();

        MemoryArchiveDocument highScore = archive(10L, "group-b", 1, "ordinary summary", "ordinary");
        MemoryArchiveDocument sharedTagAnchor = archive(11L, "group-b", 0, "alpha incident summary", "alpha incident");
        RankContext context = new RankContext(
                Set.of("alpha"),
                Map.of("group-b", Set.of("alpha")),
                Map.of("alpha", 1),
                2,
                new LinkedHashMap<>(Map.of("group-b", now.minusHours(2))),
                now
        );

        RecallResult recallResult = RecallResult.resolved(
                archive(1L, "group-a", 0, "query", "query"),
                0,
                1.0,
                "query",
                List.of(
                        match(highScore, 0.95, "group-b", 1),
                        match(sharedTagAnchor, 0.80, "group-b", 0)
                )
        );

        RankResult rankResult = ranker.rank(List.of(recallResult), context);

        LinkTarget winner = rankResult.winner();
        assertNotNull(winner);
        assertEquals("group-b", winner.groupId());
        assertEquals(11L, winner.targetArchiveId());
        assertEquals(0.95, winner.vectorSupportScore(), 1.0e-9);
        assertEquals(0.80, winner.bestScore(), 1.0e-9);
        assertEquals(List.of("alpha"), winner.sharedTags());
        assertEquals(List.of("alpha"), winner.anchorMatchedTags());
        assertTrue(winner.boostedSupportScore() > winner.timeAdjustedSupportScore());
        assertEquals(1, rankResult.candidates().size());
    }

    private static MemoryArchiveDocument archive(Long id,
                                                 String groupId,
                                                 Integer groupOrder,
                                                 String keywordSummary,
                                                 String topic) {
        MemoryArchiveDocument archive = new MemoryArchiveDocument();
        archive.setId(id);
        archive.setGroupId(groupId);
        archive.setGroupOrder(groupOrder);
        archive.setKeywordSummary(keywordSummary);
        archive.setTopic(topic);
        archive.setNarrative(keywordSummary);
        archive.setCreatedAt(LocalDateTime.now());
        return archive;
    }

    private static ArchiveEmbeddingService.ArchiveVectorMatch match(MemoryArchiveDocument archive,
                                                                    double score,
                                                                    String groupId,
                                                                    Integer groupOrder) {
        return new ArchiveEmbeddingService.ArchiveVectorMatch(
                archive,
                score,
                "vec-" + archive.getId(),
                groupId,
                null,
                groupOrder
        );
    }
}
