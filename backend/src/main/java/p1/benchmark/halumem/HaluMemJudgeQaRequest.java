package p1.benchmark.halumem;

public record HaluMemJudgeQaRequest(String question,
                                    String groundTruth,
                                    String systemAnswer,
                                    String referenceContext,
                                    String retrievedContext) {
}
