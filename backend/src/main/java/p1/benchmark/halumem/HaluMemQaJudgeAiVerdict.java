package p1.benchmark.halumem;

import dev.langchain4j.model.output.structured.Description;

public record HaluMemQaJudgeAiVerdict(
        @Description("Short reasoning for the judgement.")
        String reasoning,
        @Description("One of CORRECT, PARTIAL, WRONG, HALLUCINATED, or UNKNOWN.")
        String verdict,
        @Description("A score between 0.0 and 1.0 for the answer quality.")
        Double score,
        @Description("Whether the system answer introduces unsupported facts.")
        Boolean hallucinated) {
}
