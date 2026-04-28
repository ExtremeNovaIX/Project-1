package p1.benchmark.memory;

import org.junit.jupiter.api.Test;
import p1.component.agent.tools.MemorySearchTools;
import p1.model.document.MemoryArchiveDocument;
import p1.service.markdown.MemoryArchiveStore;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryBenchmarkSearchServiceTest {

    @Test
    void shouldExposeRankedSourceSessionsFromSourceRefs() {
        MemorySearchTools memorySearchTools = mock(MemorySearchTools.class);
        MemoryArchiveStore archiveStore = mock(MemoryArchiveStore.class);

        MemorySearchTools.MemorySearchBundle bundle = new MemorySearchTools.MemorySearchBundle(
                11L,
                "group-a",
                0.93,
                List.of(new MemorySearchTools.ArchiveNodeView(11L, "group-a", 0, "seed", "seed summary", "seed narrative")),
                List.of(new MemorySearchTools.GraphExpansionItem(
                        "same_group_external_one_hop",
                        11L,
                        21L,
                        "group-b",
                        1,
                        "target",
                        "target summary",
                        "target narrative",
                        1,
                        List.of(11L, 21L),
                        List.of("related_to")
                )),
                false
        );

        when(memorySearchTools.searchLongTermMemory("question")).thenReturn(
                new MemorySearchTools.MemorySearchResult("question", "OK", "done", List.of(bundle), false)
        );

        MemoryArchiveDocument seed = new MemoryArchiveDocument();
        seed.setId(11L);
        seed.setSourceRefs(List.of("session:s1", "custom:seed"));
        MemoryArchiveDocument target = new MemoryArchiveDocument();
        target.setId(21L);
        target.setSourceRefs(List.of("session:s2"));
        when(archiveStore.findById(11L)).thenReturn(Optional.of(seed));
        when(archiveStore.findById(21L)).thenReturn(Optional.of(target));

        MemoryBenchmarkSearchService service = new MemoryBenchmarkSearchService(memorySearchTools, archiveStore);
        BenchmarkSearchResponse response = service.search(new BenchmarkSearchRequest("bench-session", "question"));

        assertEquals("OK", response.status());
        assertEquals(1, response.documents().size());
        assertEquals(List.of("session:s1", "custom:seed", "session:s2"), response.documents().getFirst().sourceRefs());
        assertEquals(List.of("s1", "s2"), response.documents().getFirst().sourceSessionIds());
        assertEquals(List.of("s1", "s2"), response.rankedSourceSessions().stream()
                .map(BenchmarkSearchResponse.BenchmarkSourceSessionHit::sourceSessionId)
                .toList());
        assertTrue(response.documents().getFirst().text().contains("Source refs: session:s1, custom:seed, session:s2"));
    }
}
