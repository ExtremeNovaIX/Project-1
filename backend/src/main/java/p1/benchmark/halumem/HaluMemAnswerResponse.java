package p1.benchmark.halumem;

import java.util.List;

public record HaluMemAnswerResponse(String sessionId,
                                    String question,
                                    String status,
                                    String message,
                                    String answer,
                                    String thinking,
                                    boolean truncated,
                                    List<HaluMemRetrievedDocument> documents,
                                    List<String> rankedSourceSessionIds,
                                    String retrievedContext) {
}
