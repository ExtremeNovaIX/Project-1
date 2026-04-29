package p1.benchmark.halumem;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

public record HaluMemMemoryJudgeAiVerdict(
        @Description("Short reasoning about which gold memories were covered and which system memories were unsupported.")
        String reasoning,
        @Description("How many gold memory points are covered by the system memory list.")
        Integer matchedGoldCount,
        @Description("How many system memory items are supported by at least one gold memory point.")
        Integer supportedSystemCount,
        @Description("Gold memory points that are missing from the system memory list.")
        List<String> missingGoldItems,
        @Description("System memory items that are unsupported or hallucinatory relative to the gold memory points.")
        List<String> unsupportedSystemItems) {
}
