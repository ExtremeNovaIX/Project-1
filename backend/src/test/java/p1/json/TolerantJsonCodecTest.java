package p1.json;

import org.junit.jupiter.api.Test;
import p1.component.ai.service.FactExtractionAiService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TolerantJsonCodecTest {

    private final TolerantJsonCodec codec = new TolerantJsonCodec();

    @Test
    void shouldRepairSlightlyWrongFieldNamesForNestedDto() {
        String json = """
                {
                  "summery": "这是一次关于养猫偏好的对话总结",
                  "events": [
                    {
                      "topik": "养猫偏好",
                      "narrativ": "用户在纠结养纯种猫还是领养流浪猫。",
                      "keywordSummry": "养猫，纯种猫，流浪猫，领养偏好",
                      "importanceScor": 7
                    }
                  ]
                }
                """;

        FactExtractionAiService.FactExtractionResponse response =
                codec.fromJson(json, FactExtractionAiService.FactExtractionResponse.class);

        assertEquals("这是一次关于养猫偏好的对话总结", response.getSummary());
        assertNotNull(response.getEvents());
        assertEquals(1, response.getEvents().size());
        assertEquals("养猫偏好", response.getEvents().getFirst().getTopic());
        assertEquals("用户在纠结养纯种猫还是领养流浪猫。", response.getEvents().getFirst().getNarrative());
        assertEquals("养猫，纯种猫，流浪猫，领养偏好", response.getEvents().getFirst().getKeywordSummary());
        assertEquals(7, response.getEvents().getFirst().getImportanceScore());
    }

    @Test
    void shouldNotRepairTypeOutsideWhitelist() {
        String json = """
                {
                  "valu": "demo"
                }
                """;

        assertThrows(RuntimeException.class, () -> codec.fromJson(json, NonWhitelistedDto.class));
    }

    private static class NonWhitelistedDto {
        public String value;
    }
}
