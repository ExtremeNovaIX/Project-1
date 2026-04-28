package p1.benchmark.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import p1.component.agent.memory.MemoryWriteService;
import p1.component.agent.memory.model.ExtractedMemoryEvent;
import p1.utils.SessionUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemoryBenchmarkIngestionService {

    private static final int DEFAULT_CHUNK_MESSAGE_COUNT = Integer.MAX_VALUE;

    private final MemoryWriteService memoryWriteService;

    public BenchmarkIngestResponse ingest(BenchmarkIngestRequest request) {
        String sessionId = SessionUtil.normalizeSessionId(request.sessionId());
        int chunkMessageCount = resolveChunkMessageCount(request.batchMessageCount());
        int transcriptCount = 0;
        int acceptedMessageCount = 0;
        int persistedBatchCount = 0;
        int persistedEventCount = 0;

        for (BenchmarkTranscriptDTO transcript : safeTranscripts(request.transcripts())) {
            List<BenchmarkMessageDTO> messages = normalizeMessages(transcript.messages());
            if (messages.isEmpty()) {
                continue;
            }

            transcriptCount++;
            acceptedMessageCount += messages.size();
            List<ExtractedMemoryEvent> events = buildEvents(transcript, messages, chunkMessageCount);
            if (events.isEmpty()) {
                continue;
            }

            memoryWriteService.saveEventGroup(
                    sessionId,
                    events,
                    List.of(),
                    buildSourceRefs(transcript)
            );
            persistedBatchCount++;
            persistedEventCount += events.size();
        }

        return new BenchmarkIngestResponse(
                sessionId,
                transcriptCount,
                acceptedMessageCount,
                persistedBatchCount,
                persistedEventCount
        );
    }

    private int resolveChunkMessageCount(Integer requestValue) {
        if (requestValue == null || requestValue <= 0) {
            return DEFAULT_CHUNK_MESSAGE_COUNT;
        }
        return Math.max(2, requestValue);
    }

    private List<BenchmarkTranscriptDTO> safeTranscripts(List<BenchmarkTranscriptDTO> transcripts) {
        return transcripts == null ? List.of() : transcripts;
    }

    private List<BenchmarkMessageDTO> normalizeMessages(List<BenchmarkMessageDTO> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<BenchmarkMessageDTO> normalized = new ArrayList<>();
        for (BenchmarkMessageDTO message : messages) {
            if (message == null || !StringUtils.hasText(message.content())) {
                continue;
            }
            String role = normalizeRole(message.role());
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            normalized.add(new BenchmarkMessageDTO(role, message.content().trim()));
        }
        return List.copyOf(normalized);
    }

    private List<ExtractedMemoryEvent> buildEvents(BenchmarkTranscriptDTO transcript,
                                                   List<BenchmarkMessageDTO> messages,
                                                   int chunkMessageCount) {
        List<ExtractedMemoryEvent> events = new ArrayList<>();
        List<BenchmarkMessageDTO> current = new ArrayList<>();
        int eventIndex = 1;

        for (int index = 0; index < messages.size(); index++) {
            BenchmarkMessageDTO message = messages.get(index);
            current.add(message);

            boolean sizeReached = current.size() >= chunkMessageCount;
            boolean endOfTranscript = index == messages.size() - 1;
            boolean closesTurn = "assistant".equals(message.role());
            if (!endOfTranscript && !(sizeReached && closesTurn)) {
                continue;
            }

            ExtractedMemoryEvent event = toEvent(transcript, eventIndex, current);
            if (event != null) {
                events.add(event);
                eventIndex++;
            }
            current = new ArrayList<>();
        }

        if (!current.isEmpty()) {
            ExtractedMemoryEvent tailEvent = toEvent(transcript, eventIndex, current);
            if (tailEvent != null) {
                events.add(tailEvent);
            }
        }

        return List.copyOf(events);
    }

    private ExtractedMemoryEvent toEvent(BenchmarkTranscriptDTO transcript,
                                         int eventIndex,
                                         List<BenchmarkMessageDTO> messages) {
        String transcriptId = StringUtils.hasText(transcript.transcriptId())
                ? transcript.transcriptId().trim()
                : "transcript";
        String content = renderMessages(messages);
        if (content.isBlank()) {
            return null;
        }

        ExtractedMemoryEvent event = new ExtractedMemoryEvent();
        event.setTopic(transcriptId + " chunk " + String.format(Locale.ROOT, "%02d", eventIndex));
        event.setKeywordSummary(abbreviate(content, 1200));
        event.setNarrative(content);
        event.setImportanceScore(10);
        return event;
    }

    private List<String> buildSourceRefs(BenchmarkTranscriptDTO transcript) {
        Set<String> refs = new LinkedHashSet<>();
        if (transcript == null) {
            return List.of();
        }

        if (StringUtils.hasText(transcript.sourceSessionId())) {
            refs.add("session:" + transcript.sourceSessionId().trim());
        }
        if (StringUtils.hasText(transcript.transcriptId())) {
            refs.add("transcript:" + transcript.transcriptId().trim());
        }
        if (transcript.sourceRefs() != null) {
            for (String sourceRef : transcript.sourceRefs()) {
                if (StringUtils.hasText(sourceRef)) {
                    refs.add(sourceRef.trim());
                }
            }
        }
        return List.copyOf(refs);
    }

    private String renderMessages(List<BenchmarkMessageDTO> messages) {
        StringBuilder builder = new StringBuilder();
        for (BenchmarkMessageDTO message : messages) {
            if (message == null || !StringUtils.hasText(message.content())) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append("[")
                    .append(normalizeRole(message.role()).toUpperCase(Locale.ROOT))
                    .append("] ")
                    .append(message.content().trim());
        }
        return builder.toString().trim();
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "";
        }
        return role.trim().toLowerCase(Locale.ROOT);
    }

    private String abbreviate(String text, int maxLength) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
