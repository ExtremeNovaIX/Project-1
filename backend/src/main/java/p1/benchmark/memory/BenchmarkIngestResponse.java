package p1.benchmark.memory;

public record BenchmarkIngestResponse(String sessionId,
                                      int transcriptCount,
                                      int acceptedMessageCount,
                                      int persistedBatchCount,
                                      int persistedEventCount) {
}
