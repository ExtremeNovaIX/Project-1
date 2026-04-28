package p1.benchmark.memory;

import org.junit.jupiter.api.Test;
import p1.component.agent.memory.MemoryWriteService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemoryBenchmarkIngestionServiceTest {

    @Test
    void shouldPersistTranscriptAsSingleEventByDefault() {
        MemoryWriteService memoryWriteService = mock(MemoryWriteService.class);
        MemoryBenchmarkIngestionService service = new MemoryBenchmarkIngestionService(memoryWriteService);

        BenchmarkIngestRequest request = new BenchmarkIngestRequest(
                "bench-session",
                null,
                List.of(new BenchmarkTranscriptDTO(
                        "transcript-1",
                        "source-session-1",
                        List.of("custom-ref"),
                        List.of(
                                new BenchmarkMessageDTO("user", "u1"),
                                new BenchmarkMessageDTO("assistant", "a1"),
                                new BenchmarkMessageDTO("user", "u2"),
                                new BenchmarkMessageDTO("assistant", "a2")
                        )
                ))
        );

        BenchmarkIngestResponse response = service.ingest(request);

        @SuppressWarnings("unchecked")
        var eventsCaptor = forClass(List.class);
        @SuppressWarnings("unchecked")
        var sourceRefsCaptor = forClass(List.class);
        verify(memoryWriteService).saveEventGroup(
                eq("bench-session"),
                eventsCaptor.capture(),
                eq(List.of()),
                sourceRefsCaptor.capture()
        );

        assertEquals(List.of("session:source-session-1", "transcript:transcript-1", "custom-ref"), sourceRefsCaptor.getValue());
        assertEquals(1, ((List<?>) eventsCaptor.getValue()).size());
        assertEquals(1, response.transcriptCount());
        assertEquals(4, response.acceptedMessageCount());
        assertEquals(1, response.persistedBatchCount());
        assertEquals(1, response.persistedEventCount());
    }

    @Test
    void shouldHonorExplicitChunkMessageCount() {
        MemoryWriteService memoryWriteService = mock(MemoryWriteService.class);
        MemoryBenchmarkIngestionService service = new MemoryBenchmarkIngestionService(memoryWriteService);

        BenchmarkIngestRequest request = new BenchmarkIngestRequest(
                "bench-session",
                2,
                List.of(new BenchmarkTranscriptDTO(
                        "transcript-1",
                        "source-session-1",
                        List.of(),
                        List.of(
                                new BenchmarkMessageDTO("user", "u1"),
                                new BenchmarkMessageDTO("assistant", "a1"),
                                new BenchmarkMessageDTO("user", "u2"),
                                new BenchmarkMessageDTO("assistant", "a2")
                        )
                ))
        );

        BenchmarkIngestResponse response = service.ingest(request);

        @SuppressWarnings("unchecked")
        var eventsCaptor = forClass(List.class);
        verify(memoryWriteService).saveEventGroup(
                eq("bench-session"),
                eventsCaptor.capture(),
                eq(List.of()),
                eq(List.of("session:source-session-1", "transcript:transcript-1"))
        );

        assertEquals(2, ((List<?>) eventsCaptor.getValue()).size());
        assertEquals(2, response.persistedEventCount());
    }
}
