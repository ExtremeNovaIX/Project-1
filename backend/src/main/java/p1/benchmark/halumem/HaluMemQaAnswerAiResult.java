package p1.benchmark.halumem;

import dev.langchain4j.model.output.structured.Description;

public record HaluMemQaAnswerAiResult(
        @Description("Brief internal reasoning used to derive the final answer from the provided memory context.")
        String thinking,
        @Description("The final answer grounded only in the supplied memory context. If the context is insufficient, say that explicitly.")
        String answer) {
}
