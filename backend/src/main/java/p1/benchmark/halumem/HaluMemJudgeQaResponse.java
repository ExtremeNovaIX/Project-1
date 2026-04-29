package p1.benchmark.halumem;

public record HaluMemJudgeQaResponse(String verdict,
                                     double score,
                                     boolean hallucinated,
                                     String reasoning) {
}
