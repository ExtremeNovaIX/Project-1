package p1.benchmark.halumem;

import java.util.List;

public record HaluMemSessionIngestResponse(String sessionId,
                                           String sessionLabel,
                                           int acceptedMessageCount,
                                           int extractedMemoryCount,
                                           int persistedArchiveCount,
                                           List<String> tags,
                                           String summary,
                                           List<HaluMemMemoryItem> extractedMemories,
                                           List<HaluMemMemoryItem> currentMemories) {
}
