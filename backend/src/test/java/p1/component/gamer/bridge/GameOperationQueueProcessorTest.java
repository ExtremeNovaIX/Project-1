package p1.component.gamer.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;
import p1.component.agent.gamer.adapter.GameAdapter;
import p1.component.agent.gamer.adapter.GameAdapterContext;
import p1.component.agent.gamer.adapter.GameStateSnapshot;
import p1.component.agent.gamer.adapter.QueuedGameOperation;
import p1.component.agent.gamer.bridge.GameOperationQueueProcessor;
import p1.component.agent.gamer.memory.GamerMemoryCompressorAiService;
import p1.component.agent.gamer.memory.GamerWorkingMemoryService;
import p1.component.agent.reasoning.ReasoningContentRecorder;
import p1.config.mcp.GamerMemoryProperties;
import p1.config.mcp.MCPProperties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameOperationQueueProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSkipSoftFailureAndContinueRemainingQueue() throws Exception {
        GameOperationQueueProcessor processor = new GameOperationQueueProcessor(testWorkingMemoryService());
        GameStateSnapshot playState = state("{\"state_type\":\"monster\",\"battle\":{\"turn\":\"player\",\"is_play_phase\":true}}");
        SoftAwareAdapter adapter = new SoftAwareAdapter(playState);
        String memoryId = "soft-failure-test";
        processor.rememberPlanningState(memoryId, playState);

        String result = processor.enqueueAndDrain(
                "test-game",
                memoryId,
                adapter,
                config(),
                tools(),
                """
                        {
                          "status":"CONTINUE",
                          "summary":"测试软错误继续",
                          "operations":[
                            {"tool":"bad_tool","args":{},"note":"失败操作"},
                            {"tool":"good_tool","args":{},"note":"成功操作"}
                          ]
                        }
                        """
        );

        assertTrue(result.contains("跳过 1 条软错误操作"));
        assertTrue(processor.consumeNotice(memoryId).contains("bad_tool"));
        assertTrue(processor.peekLastActionResult(memoryId).contains("state_diff"));
        assertTrue(processor.peekLastActionResult(memoryId).contains("bad_tool"));
    }

    @Test
    void shouldInterruptFailureWhenLatestStateIsNotActionable() throws Exception {
        GameOperationQueueProcessor processor = new GameOperationQueueProcessor(testWorkingMemoryService());
        GameStateSnapshot playState = state("{\"state_type\":\"monster\",\"battle\":{\"turn\":\"player\",\"is_play_phase\":true}}");
        GameStateSnapshot rewardsState = state("{\"state_type\":\"rewards\"}");
        SoftAwareAdapter adapter = new SoftAwareAdapter(rewardsState);
        String memoryId = "hard-failure-test";
        processor.rememberPlanningState(memoryId, playState);

        String result = processor.enqueueAndDrain(
                "test-game",
                memoryId,
                adapter,
                config(),
                tools(),
                """
                        {
                          "status":"CONTINUE",
                          "summary":"测试硬中断",
                          "operations":[
                            {"tool":"bad_tool","args":{},"note":"失败操作"},
                            {"tool":"good_tool","args":{},"note":"不应执行"}
                          ]
                        }
                        """
        );

        assertTrue(result.contains("操作队列已中断"));
        String notice = processor.consumeNotice(memoryId);
        assertTrue(notice.contains("MCP 工具执行失败"));
        assertFalse(notice.contains("最新状态"));
        assertTrue(processor.peekLastActionResult(memoryId).contains("队列中断"));
    }

    @Test
    void shouldKeepReasoningContentOutOfPromptFeedback() throws Exception {
        ReasoningContentRecorder recorder = new ReasoningContentRecorder();
        GameOperationQueueProcessor processor = new GameOperationQueueProcessor(testWorkingMemoryService(), null, recorder);
        GameStateSnapshot playState = state("{\"state_type\":\"monster\",\"battle\":{\"turn\":\"player\",\"is_play_phase\":true}}");
        String memoryId = "reasoning-test";
        processor.rememberPlanningState(memoryId, playState);
        recorder.recordLatest(memoryId, "先处理高收益操作，再观察状态变化。");

        processor.enqueueAndDrain(
                "test-game",
                memoryId,
                new SoftAwareAdapter(playState),
                config(),
                tools(),
                """
                        {
                          "status":"CONTINUE",
                          "summary":"测试推理保留",
                          "operations":[
                            {"tool":"good_tool","args":{},"note":"成功操作"}
                          ]
                        }
                        """
        );

        assertFalse(processor.peekLastActionResult(memoryId).contains("reasoning_content"));
        assertFalse(processor.peekLastActionResult(memoryId).contains("先处理高收益操作"));
    }

    @Test
    void shouldGenerateSummaryWhenModelLeavesSummaryBlank() throws Exception {
        GameOperationQueueProcessor processor = new GameOperationQueueProcessor(testWorkingMemoryService());
        GameStateSnapshot playState = state("{\"state_type\":\"monster\",\"battle\":{\"turn\":\"player\",\"is_play_phase\":true}}");
        String memoryId = "blank-summary-test";
        processor.rememberPlanningState(memoryId, playState);

        String result = processor.enqueueAndDrain(
                "test-game",
                memoryId,
                new SoftAwareAdapter(playState),
                config(),
                tools(),
                """
                        {
                          "status":"CONTINUE",
                          "summary":"",
                          "operations":[
                            {"tool":"good_tool","args":{},"note":"成功操作"}
                          ]
                        }
                        """
        );

        assertTrue(result.contains("执行：成功操作"));
        assertTrue(processor.peekLastActionResult(memoryId).contains("decision=执行：成功操作"));
    }

    @Test
    void shouldAcceptDecisionSummaryAsCompatibilityField() throws Exception {
        GameOperationQueueProcessor processor = new GameOperationQueueProcessor(testWorkingMemoryService());
        GameStateSnapshot playState = state("{\"state_type\":\"monster\",\"battle\":{\"turn\":\"player\",\"is_play_phase\":true}}");
        String memoryId = "decision-summary-test";
        processor.rememberPlanningState(memoryId, playState);

        processor.enqueueAndDrain(
                "test-game",
                memoryId,
                new SoftAwareAdapter(playState),
                config(),
                tools(),
                """
                        {
                          "status":"CONTINUE",
                          "decision_summary":"兼容旧字段并继续执行",
                          "operations":[
                            {"tool":"good_tool","args":{},"note":"成功操作"}
                          ]
                        }
                        """
        );

        assertTrue(processor.peekLastActionResult(memoryId).contains("decision=兼容旧字段并继续执行"));
    }

    private GamerWorkingMemoryService testWorkingMemoryService() {
        GamerMemoryCompressorAiService compressor = new GamerMemoryCompressorAiService() {
            @Override
            public String compressStage(String previousSummary, String trigger, String decisions) {
                return previousSummary + "\n" + trigger + "\n" + decisions;
            }

            @Override
            public String compressRun(String previousRunSummary, String stageSummary) {
                return previousRunSummary + "\n" + stageSummary;
            }
        };
        return new GamerWorkingMemoryService(new GamerMemoryProperties(), compressor);
    }

    private MCPProperties.GameMCPConfig config() {
        MCPProperties.GameMCPConfig config = new MCPProperties.GameMCPConfig();
        config.setStateSettleMaxAttempts(1);
        config.setStateSettleDelayMs(0);
        return config;
    }

    private ToolProviderResult tools() {
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        builder.add(tool("bad_tool"), (request, memoryId) -> "{\"status\":\"error\",\"error\":\"bad operation\"}");
        builder.add(tool("good_tool"), (request, memoryId) -> "{\"status\":\"ok\",\"message\":\"done\"}");
        return builder.build();
    }

    private ToolSpecification tool(String name) {
        return ToolSpecification.builder()
                .name(name)
                .description(name)
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    private GameStateSnapshot state(String raw) throws Exception {
        return new GameStateSnapshot(raw, objectMapper.readTree(raw), objectMapper.readTree(raw).path("state_type").asText(""));
    }

    private static class SoftAwareAdapter implements GameAdapter {
        private final GameStateSnapshot latestState;

        private SoftAwareAdapter(GameStateSnapshot latestState) {
            this.latestState = latestState;
        }

        @Override
        public String id() {
            return "soft-aware";
        }

        @Override
        public GameStateSnapshot fetchState(GameAdapterContext context) {
            return latestState;
        }

        @Override
        public boolean shouldContinueAfterOperationFailure(QueuedGameOperation operation,
                                                           GameStateSnapshot beforeState,
                                                           GameStateSnapshot afterState,
                                                           String reason) {
            return "monster".equals(afterState.stateType())
                    && "player".equals(afterState.json().path("battle").path("turn").asText(""))
                    && afterState.json().path("battle").path("is_play_phase").asBoolean(false);
        }
    }
}
