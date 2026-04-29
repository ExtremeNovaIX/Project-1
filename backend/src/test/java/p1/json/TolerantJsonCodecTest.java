package p1.json;

import org.junit.jupiter.api.Test;
import p1.component.agent.memory.FactExtractionService;
import p1.utils.json.TolerantJsonCodec;

import static org.junit.jupiter.api.Assertions.*;

class TolerantJsonCodecTest {

    private final TolerantJsonCodec codec = new TolerantJsonCodec();

    @Test
    void shouldRepairNoiseBetweenArrayItemsInFencedJson() {
        String json = """
                ```json
                {
                  "events": [
                    {
                      "topic": "first event",
                      "narrative": "first narrative",
                      "scoreReason": "first reason",
                      "importanceScore": 7
                    },
                ly   {
                      "topic": "second event",
                      "narrative": "second narrative",
                      "scoreReason": "second reason",
                      "importanceScore": 8
                    }
                  ]
                }
                ```
                """;

        FactExtractionService.FactExtractionDTO result = codec.fromJson(json, FactExtractionService.FactExtractionDTO.class);

        assertEquals(2, result.payloadEvents().size());
        assertEquals("first event", result.payloadEvents().get(0).getTopic());
        assertEquals(8, result.payloadEvents().get(1).getImportanceScore());
    }

    @Test
    void shouldKeepWholeRootWhenMalformedJsonCannotBeRepaired() {
        String json = """
                ```json
                {
                  "events": [
                    {
                      "topic": "only event",
                      "narrative": "only narrative",
                      "scoreReason": "only reason",
                      "importanceScore": 6
                    }
                  ]
                  "tail": "still malformed"
                }
                ```
                """;

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> codec.fromJson(json, FactExtractionService.FactExtractionDTO.class)
        );

        String message = rootMessage(exception);
        assertTrue(message.contains("Unexpected character"));
        assertFalse(message.contains("Unrecognized field \"topic\""));
    }

    @Test
    void shouldRepairTypoFieldNameForFactScoringDto() {
        String json = """
                ```json
                {
                  "events": [
                    {
                      "topic": "conflict-upgraded",
                      "keywordswSummary": "The confrontation escalated and became a long-term memory candidate."
                    }
                  ],
                  "summary": "The batch centered on a conflict escalation."
                }
                ```
                """;

        FactExtractionService.FactSummaryDTO result = codec.fromJson(json, FactExtractionService.FactSummaryDTO.class);

        assertEquals(1, result.payloadEvents().size());
        assertEquals("conflict-upgraded", result.payloadEvents().getFirst().getTopic());
        assertEquals(
                "The confrontation escalated and became a long-term memory candidate.",
                result.payloadEvents().getFirst().getKeywordSummary()
        );
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + String.valueOf(current.getMessage());
    }
}
