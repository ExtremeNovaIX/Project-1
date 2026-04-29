package p1.benchmark.halumem;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface HaluMemMemoryJudgeAiService {

    @SystemMessage("""
            You are evaluating a memory system.
            Compare gold memory points against system memory items.

            Definitions:
            - A gold memory point is covered if the system memory list contains the same fact or a semantically equivalent fact.
            - A system memory item is supported if it can be justified by at least one gold memory point.
            - Be strict about contradictions and unsupported additions.

            Rules:
            - matchedGoldCount must be between 0 and goldCount.
            - supportedSystemCount must be between 0 and systemCount.
            - missingGoldItems should contain only truly missing gold items.
            - unsupportedSystemItems should contain only system items that are unsupported or contradictory.
            - missingGoldItems must contain at most 3 representative examples.
            - unsupportedSystemItems must contain at most 3 representative examples.
            - Return raw JSON only.
            - Do not wrap the JSON in markdown code fences.
            - The output must be valid JSON matching the schema.
            """)
    @UserMessage("""
            <gold_count>{{goldCount}}</gold_count>
            <system_count>{{systemCount}}</system_count>

            <gold_memory_points>
            {{goldMemories}}
            </gold_memory_points>

            <system_memory_items>
            {{systemMemories}}
            </system_memory_items>
            """)
    HaluMemMemoryJudgeAiVerdict judge(@V("goldCount") int goldCount,
                                      @V("systemCount") int systemCount,
                                      @V("goldMemories") String goldMemories,
                                      @V("systemMemories") String systemMemories);
}
