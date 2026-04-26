package p1.component.agent.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import p1.component.agent.context.SummaryCacheManager;
import p1.component.agent.memory.model.FactExtractionPipelineResult;
import p1.component.agent.memory.model.ExtractedMemoryEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MemoryAsyncCompressorTest {

    @Test
    void shouldWriteOnlyImportantEventsAndPersistFinalTags() {
        SummaryCacheManager summaryCacheManager = mock(SummaryCacheManager.class);
        FactExtractionService factExtractionService = mock(FactExtractionService.class);
        MemoryWriteService memoryWriteService = mock(MemoryWriteService.class);

        MemoryAsyncCompressor compressor = new MemoryAsyncCompressor(
                summaryCacheManager,
                factExtractionService,
                memoryWriteService
        );

        FactExtractionService.ExtractedFactEventDTO lowScore = new FactExtractionService.ExtractedFactEventDTO();
        lowScore.setTopic("small-talk");
        lowScore.setNarrative("Just a casual greeting.");
        lowScore.setScoreReason("Not important.");
        lowScore.setImportanceScore(3);

        FactExtractionService.ExtractedFactEventDTO highScore = new FactExtractionService.ExtractedFactEventDTO();
        highScore.setTopic("relationship-progress");
        highScore.setNarrative("The user described stronger emotional dependence.");
        highScore.setScoreReason("This changes the long-term relationship dynamic.");
        highScore.setImportanceScore(8);

        FactExtractionService.ExtractedFactEventDTO noise = new FactExtractionService.ExtractedFactEventDTO();
        noise.setTopic("noise");
        noise.setNarrative("invalid test");
        noise.setScoreReason("Noise only.");
        noise.setImportanceScore(1);

        FactExtractionService.FactSummaryEventDTO summaryEvent = new FactExtractionService.FactSummaryEventDTO();
        summaryEvent.setTopic("relationship-progress");
        summaryEvent.setKeywordSummary("The user expressed stronger emotional dependence.");

        FactExtractionService.FactSummaryDTO summary = new FactExtractionService.FactSummaryDTO();
        summary.setTags(List.of("dependency-shift", "relationship"));
        summary.setEvents(List.of(summaryEvent));
        summary.setSummary("This batch centers on relationship progress.");

        ExtractedMemoryEvent mergedEvent = new ExtractedMemoryEvent();
        mergedEvent.setTopic("relationship-progress");
        mergedEvent.setNarrative("The user described stronger emotional dependence.");
        mergedEvent.setKeywordSummary("The user expressed stronger emotional dependence.");
        mergedEvent.setImportanceScore(8);

        FactExtractionPipelineResult pipelineResult = new FactExtractionPipelineResult(
                List.of(mergedEvent),
                List.of("dependency-shift", "relationship"),
                "This batch centers on relationship progress."
        );

        when(factExtractionService.extractFact(anyList(), eq("test")))
                .thenReturn(List.of(lowScore, highScore, noise));
        when(factExtractionService.summarizeFacts(anyList())).thenReturn(summary);
        when(factExtractionService.buildPipelineResult(anyList(), eq(summary))).thenReturn(pipelineResult);

        AtomicInteger successCounter = new AtomicInteger();
        AtomicInteger failureCounter = new AtomicInteger();
        List<ChatMessage> messages = List.of(
                UserMessage.from("I have become more dependent on you lately."),
                AiMessage.from("I will remember that change.")
        );

        compressor.compressAsync("test", messages, successCounter::incrementAndGet, failureCounter::incrementAndGet);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FactExtractionService.ExtractedFactEventDTO>> importantCaptor = ArgumentCaptor.forClass(List.class);
        verify(factExtractionService).summarizeFacts(importantCaptor.capture());
        assertEquals(1, importantCaptor.getValue().size());
        assertEquals("relationship-progress", importantCaptor.getValue().getFirst().getTopic());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExtractedMemoryEvent>> eventCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> tagCaptor = ArgumentCaptor.forClass(List.class);
        verify(memoryWriteService).saveEventGroup(eq("test"), eventCaptor.capture(), tagCaptor.capture());
        verify(summaryCacheManager).updateSummary("test", "This batch centers on relationship progress.");

        List<ExtractedMemoryEvent> savedEvents = eventCaptor.getValue();
        assertEquals(1, savedEvents.size());
        assertEquals("relationship-progress", savedEvents.getFirst().getTopic());
        assertEquals(List.of("dependency-shift", "relationship"), tagCaptor.getValue());
        assertEquals(1, successCounter.get());
        assertEquals(0, failureCounter.get());
    }
}
