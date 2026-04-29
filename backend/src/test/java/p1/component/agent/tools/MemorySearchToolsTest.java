package p1.component.agent.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import p1.component.agent.memory.model.ArchiveLink;
import p1.infrastructure.vector.ArchiveVectorLibrary;
import p1.model.document.MemoryArchiveDocument;
import p1.model.document.RecentEventGroupDocument;
import p1.service.archive.ArchiveEmbeddingService;
import p1.service.markdown.MemoryArchiveStore;
import p1.service.markdown.RecentEventGroupMarkdownService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemorySearchToolsTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldReturnGroupContextAndPrioritizedGraphExpansion() {
        MDC.put("sessionId", "session-a");

        ArchiveEmbeddingService archiveEmbeddingService = mock(ArchiveEmbeddingService.class);
        MemoryArchiveStore archiveStore = mock(MemoryArchiveStore.class);
        RecentEventGroupMarkdownService recentEventGroupService = mock(RecentEventGroupMarkdownService.class);

        MemoryArchiveDocument seed = archive(11L, "group-a", 0, "seed", "seed summary", "seed narrative");
        seed.setLinks(List.of(
                new ArchiveLink("related_to", 41L, "mid", 0.9, "seed to mid"),
                new ArchiveLink("next_in_time", 12L, "mate-1", 1.0, "timeline")
        ));

        MemoryArchiveDocument groupMate1 = archive(12L, "group-a", 1, "mate-1", "mate-1 summary", "mate-1 narrative");
        groupMate1.setLinks(List.of(
                new ArchiveLink("recent_window_to", 61L, "external-1", 0.88, "group to external")
        ));

        MemoryArchiveDocument groupMate2 = archive(13L, "group-a", 2, "mate-2", "mate-2 summary", "mate-2 narrative");
        groupMate2.setLinks(List.of(
                new ArchiveLink("next_in_time", 12L, "mate-1", 1.0, "timeline")
        ));

        MemoryArchiveDocument mid = archive(41L, "group-b", 3, "mid", "mid summary", "mid narrative");
        mid.setLinks(List.of(
                new ArchiveLink("caused_by", 51L, "external-2", 0.82, "mid to external")
        ));

        MemoryArchiveDocument externalTwoHop = archive(51L, "group-c", 5, "external-2", "external-2 summary", "external-2 narrative");
        MemoryArchiveDocument externalOneHop = archive(61L, "group-d", 4, "external-1", "external-1 summary", "external-1 narrative");

        when(archiveEmbeddingService.searchArchiveMatches(
                eq("session-a"),
                eq(ArchiveVectorLibrary.ARCHIVE),
                eq("用户记得童年旅行"),
                eq(3),
                eq(0.5)
        )).thenReturn(List.of(new ArchiveEmbeddingService.ArchiveVectorMatch(seed, 0.93, "archive-11", null, null, null)));

        RecentEventGroupDocument group = new RecentEventGroupDocument();
        group.setId("group-a");
        group.setSessionId("session-a");
        group.setArchiveIds(List.of(13L, 11L, 12L));
        when(recentEventGroupService.findById("session-a", "group-a")).thenReturn(Optional.of(group));

        when(archiveStore.findById(11L)).thenReturn(Optional.of(seed));
        when(archiveStore.findById(12L)).thenReturn(Optional.of(groupMate1));
        when(archiveStore.findById(13L)).thenReturn(Optional.of(groupMate2));
        when(archiveStore.findById(41L)).thenReturn(Optional.of(mid));
        when(archiveStore.findById(51L)).thenReturn(Optional.of(externalTwoHop));
        when(archiveStore.findById(61L)).thenReturn(Optional.of(externalOneHop));

        MemorySearchTools tools = new MemorySearchTools(archiveEmbeddingService, archiveStore, recentEventGroupService);
        MemorySearchTools.MemorySearchResult result = tools.searchLongTermMemory("用户记得童年旅行");

        assertEquals("OK", result.status());
        assertEquals(1, result.bundles().size());
        assertFalse(result.truncated());

        MemorySearchTools.MemorySearchBundle bundle = result.bundles().getFirst();
        assertEquals(List.of(11L, 12L, 13L), bundle.groupContext().stream().map(MemorySearchTools.ArchiveNodeView::archiveId).toList());
        assertEquals(3, bundle.graphExpansion().size());

        MemorySearchTools.GraphExpansionItem firstExpansion = bundle.graphExpansion().get(0);
        assertEquals("seed_external_two_hop", firstExpansion.priorityBucket());
        assertEquals(51L, firstExpansion.archiveId());
        assertEquals(List.of(11L, 41L, 51L), firstExpansion.pathArchiveIds());
        assertEquals(List.of("related_to", "caused_by"), firstExpansion.pathRelations());

        MemorySearchTools.GraphExpansionItem secondExpansion = bundle.graphExpansion().get(1);
        assertEquals("seed_external_two_hop", secondExpansion.priorityBucket());
        assertEquals(61L, secondExpansion.archiveId());
        assertEquals(List.of(11L, 12L, 61L), secondExpansion.pathArchiveIds());
        assertEquals(List.of("next_in_time", "recent_window_to"), secondExpansion.pathRelations());

        MemorySearchTools.GraphExpansionItem thirdExpansion = bundle.graphExpansion().get(2);
        assertEquals("same_group_external_one_hop", thirdExpansion.priorityBucket());
        assertEquals(41L, thirdExpansion.archiveId());
        assertEquals(List.of(11L, 41L), thirdExpansion.pathArchiveIds());
        assertEquals(List.of("related_to"), thirdExpansion.pathRelations());
    }

    private MemoryArchiveDocument archive(Long id,
                                          String groupId,
                                          Integer groupOrder,
                                          String topic,
                                          String keywordSummary,
                                          String narrative) {
        MemoryArchiveDocument archive = new MemoryArchiveDocument();
        archive.setId(id);
        archive.setSessionId("session-a");
        archive.setGroupId(groupId);
        archive.setGroupOrder(groupOrder);
        archive.setTopic(topic);
        archive.setKeywordSummary(keywordSummary);
        archive.setNarrative(narrative);
        return archive;
    }
}
