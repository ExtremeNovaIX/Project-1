package p1.service.archivegraph;

import org.junit.jupiter.api.Test;
import p1.config.prop.AssistantProperties;
import p1.model.document.MemoryArchiveDocument;
import p1.service.ArchiveEmbeddingService;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecentWindowRecallTest {

    @Test
    void shouldSkipBlankQueriesAndFilterRootAndSameGroupMatches() {
        AssistantProperties props = new AssistantProperties();
        ArchiveEmbeddingService archiveEmbeddingService = mock(ArchiveEmbeddingService.class);

        MemoryArchiveDocument rootArchive = archive(1L, "group-a", 0, "root summary", "alpha topic");
        MemoryArchiveDocument blankArchive = archive(2L, "group-a", 1, "   ", "blank topic");
        MemoryArchiveDocument sameRoot = archive(1L, "group-b", 0, "same root", "same root");
        MemoryArchiveDocument sameGroup = archive(3L, "group-a", 1, "same group", "same group");
        MemoryArchiveDocument kept = archive(4L, "group-b", 2, "kept match", "kept match");

        when(archiveEmbeddingService.searchArchiveMatches(eq("s1"), eq(p1.infrastructure.vector.ArchiveVectorLibrary.RECENT_24H), anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(
                        match(sameRoot, 0.92, "group-b", 0),
                        match(sameGroup, 0.88, "group-a", 1),
                        match(kept, 0.85, "group-b", 2)
                ));

        RecentWindowRecall recall = new RecentWindowRecall(props, archiveEmbeddingService);

        List<RecallResult> results = recall.recall(
                "s1",
                rootArchive,
                "group-a",
                List.of(rootArchive, blankArchive)
        );

        assertEquals(2, results.size());
        assertEquals(1, results.getFirst().usableMatches().size());
        assertEquals(4L, results.getFirst().usableMatches().getFirst().archive().getId());
        assertTrue(results.get(1).skipped());
        assertEquals("缺少 keywordSummary", results.get(1).skippedReason());
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
