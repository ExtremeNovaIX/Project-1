package p1.benchmark.memory;

import java.util.List;

public record BenchmarkSearchResponse(String sessionId,
                                      String query,
                                      String status,
                                      String message,
                                      boolean truncated,
                                      List<BenchmarkSearchDocument> documents,
                                      List<BenchmarkSourceSessionHit> rankedSourceSessions) {

    public record BenchmarkSearchDocument(String documentId,
                                          Long seedArchiveId,
                                          String seedGroupId,
                                          double seedScore,
                                          List<String> sourceRefs,
                                          List<String> sourceSessionIds,
                                          List<Long> groupContextArchiveIds,
                                          List<Long> graphExpansionArchiveIds,
                                          String text) {
    }

    public record BenchmarkSourceSessionHit(String sourceSessionId,
                                            double scoreHint,
                                            List<String> sourceRefs,
                                            List<String> documentIds) {
    }
}
