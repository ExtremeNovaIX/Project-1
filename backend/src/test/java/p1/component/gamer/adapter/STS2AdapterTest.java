package p1.component.gamer.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class STS2AdapterTest {

    private final STS2Adapter adapter = new STS2Adapter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRepairPlayCardIndexByCardName() throws Exception {
        GameStateSnapshot planned = state("""
                {
                  "state_type":"monster",
                  "player":{"hand":[
                    {"name":"打击"},
                    {"name":"防御"},
                    {"name":"中和"}
                  ]}
                }
                """);
        GameStateSnapshot current = state("""
                {
                  "state_type":"monster",
                  "player":{"hand":[
                    {"name":"打击"},
                    {"name":"中和"}
                  ]}
                }
                """);

        GameOperation operation = new GameOperation(
                "combat_play_card",
                objectMapper.readTree("{\"card_index\":2,\"target\":\"NIBBIT_0\"}"),
                "打出中和"
        );
        QueuedGameOperation queued = adapter.prepareOperation(operation, planned);

        ToolExecutionRequest repaired = adapter.repairBeforeExecute(queued, current);

        assertEquals("{\"card_index\":1,\"target\":\"NIBBIT_0\"}", repaired.arguments());
    }

    @Test
    void shouldThrowWhenPlannedCardIsGone() throws Exception {
        GameStateSnapshot planned = state("""
                {"state_type":"monster","player":{"hand":[{"name":"打击"},{"name":"防御"}]}}
                """);
        GameStateSnapshot current = state("""
                {"state_type":"monster","player":{"hand":[{"name":"打击"}]}}
                """);
        GameOperation operation = new GameOperation(
                "combat_play_card",
                objectMapper.readTree("{\"card_index\":1}"),
                "打出防御"
        );
        QueuedGameOperation queued = adapter.prepareOperation(operation, planned);

        assertThrows(GameBridgeException.class, () -> adapter.repairBeforeExecute(queued, current));
    }

    @Test
    void shouldInterruptWhenStateTypeChanges() throws Exception {
        QueuedGameOperation operation = QueuedGameOperation.from(
                new GameOperation("combat_end_turn", objectMapper.readTree("{}"), ""),
                state("{\"state_type\":\"monster\",\"player\":{\"hand\":[]}}")
        );

        GameBridgeException ex = assertThrows(GameBridgeException.class, () ->
                adapter.monitorAfterExecute(
                        operation,
                        state("{\"state_type\":\"monster\",\"player\":{\"hand\":[]}}"),
                        state("{\"state_type\":\"rewards\",\"player\":{\"hand\":[]}}"),
                        "{}",
                        true
                )
        );

        assertTrue(ex.getMessage().contains("state_type"));
    }

    @Test
    void shouldInterruptWhenPlayCardHandDeltaIsUnexpected() throws Exception {
        GameStateSnapshot planned = state("""
                {"state_type":"monster","player":{"hand":[{"name":"空翻"},{"name":"打击"}]}}
                """);
        GameOperation operation = new GameOperation(
                "combat_play_card",
                objectMapper.readTree("{\"card_index\":0}"),
                "打出空翻"
        );
        QueuedGameOperation queued = adapter.prepareOperation(operation, planned);

        GameBridgeException ex = assertThrows(GameBridgeException.class, () ->
                adapter.monitorAfterExecute(
                        queued,
                        state("{\"state_type\":\"monster\",\"player\":{\"hand\":[{\"name\":\"空翻\"},{\"name\":\"打击\"}]}}"),
                        state("{\"state_type\":\"monster\",\"player\":{\"hand\":[{\"name\":\"打击\"},{\"name\":\"防御\"},{\"name\":\"打击\"}]}}"),
                        "{}",
                        true
                )
        );

        assertTrue(ex.getMessage().contains("手牌数量"));
    }

    @Test
    void shouldIgnoreUnexpectedHandDeltaWhenNoRemainingOperations() throws Exception {
        GameStateSnapshot planned = state("""
                {"state_type":"monster","player":{"hand":[{"name":"打击"},{"name":"打击"},{"name":"防御"},{"name":"打击"}]}}
                """);
        GameOperation operation = new GameOperation(
                "combat_play_card",
                objectMapper.readTree("{\"card_index\":3,\"target\":\"FUZZY_WURM_CRAWLER_0\"}"),
                "最后一张计划内攻击"
        );
        QueuedGameOperation queued = adapter.prepareOperation(operation, planned);

        assertDoesNotThrow(() ->
                adapter.monitorAfterExecute(
                        queued,
                        state("{\"state_type\":\"monster\",\"player\":{\"hand\":[{\"name\":\"打击\"},{\"name\":\"打击\"},{\"name\":\"防御\"},{\"name\":\"打击\"}]}}"),
                        state("{\"state_type\":\"monster\",\"player\":{\"hand\":[]}}"),
                        "{}",
                        false
                )
        );
    }

    private GameStateSnapshot state(String raw) throws Exception {
        return new GameStateSnapshot(raw, objectMapper.readTree(raw), objectMapper.readTree(raw).path("state_type").asText(""));
    }
}
