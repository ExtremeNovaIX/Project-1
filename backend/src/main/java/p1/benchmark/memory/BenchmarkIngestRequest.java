package p1.benchmark.memory;

import java.util.List;

public record BenchmarkIngestRequest(String sessionId,
                                     Integer batchMessageCount,
                                     List<BenchmarkTranscriptDTO> transcripts) {
}
