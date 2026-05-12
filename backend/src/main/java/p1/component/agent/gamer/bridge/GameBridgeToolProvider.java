package p1.component.agent.gamer.bridge;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.RequiredArgsConstructor;
import p1.component.agent.gamer.adapter.GameAdapter;
import p1.config.mcp.MCPProperties;

import java.util.List;

/**
 * 桥接层工具提供器。
 * <p>
 * 它隐藏底层 MCP 工具，只向 gamer agent 暴露 enqueue_operations，
 * 从而把“多操作计划”和“逐条 MCP 调用”分离开。
 */
@RequiredArgsConstructor
public class GameBridgeToolProvider implements ToolProvider {
    public static final String ENQUEUE_TOOL_NAME = "enqueue_operations";

    private final String gameName;
    private final MCPProperties.GameMCPConfig config;
    /**
     * 当前游戏适配器。
     */
    private final GameAdapter adapter;
    /**
     * 原始 MCP ToolProvider。
     */
    private final ToolProvider delegate;
    /**
     * 操作队列处理器。
     */
    private final GameOperationQueueProcessor queueProcessor;

    /**
     * 向 LangChain4j 提供工具定义和执行器。
     *
     * @param request LangChain4j 的工具请求上下文
     * @return 只包含虚拟批量工具的 ToolProviderResult
     */
    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        // 先获取底层 MCP 工具，后续队列处理器会按 tool 名转发到这些真实工具。
        ToolProviderResult rawTools = delegate.provideTools(request);
        return ToolProviderResult.builder()
                .add(enqueueToolSpec(), (toolRequest, memoryId) ->
                                queueProcessor.enqueueAndDrain(
                                        gameName,
                                        String.valueOf(memoryId),
                                        adapter,
                                        config,
                                        rawTools,
                                        toolRequest.arguments()),
                        ReturnBehavior.IMMEDIATE)
                .build();
    }

    /**
     * 构建 enqueue_operations 的工具规格。
     *
     * @return 虚拟批量工具的 JSON schema 和说明
     */
    private ToolSpecification enqueueToolSpec() {
        // 每条操作的 args 原样透传给底层 MCP 工具，因此允许任意对象属性。
        JsonObjectSchema argsSchema = JsonObjectSchema.builder()
                .description("原始 MCP 工具参数对象")
                .additionalProperties(true)
                .build();

        // operationSchema 描述队列中的一条 MCP 操作。
        JsonObjectSchema operationSchema = JsonObjectSchema.builder()
                .description("一条待排队游戏操作。tool 必须使用系统上下文列出的 MCP 工具名。")
                .addStringProperty("tool", "MCP 工具名，必须从 available_operations 中选择")
                .addProperty("args", argsSchema)
                .addStringProperty("note", "这条操作的简短意图，便于中断诊断")
                .required("tool")
                .additionalProperties(false)
                .build();

        // parameters 是模型实际调用 enqueue_operations 时需要提交的外层结构。
        JsonObjectSchema parameters = JsonObjectSchema.builder()
                .description("提交一批游戏操作。桥接层会逐条执行，每条执行后都会刷新状态并调用适配器监视。")
                .addEnumProperty("status", List.of("CONTINUE", "WAIT", "GAME_OVER"),
                        "本批操作后的预期状态。没有可执行操作时填 WAIT。")
                .addStringProperty("summary", "必填。本批操作的最终决策摘要；只写结论和关键依据，不写推导过程，不输出 JSON 文本")
                .addStringProperty("message", "用户可见的自然语言回复（1-3句话）。必须在提交操作前向用户简要说明当前计划。")
                .addProperty("operations", JsonArraySchema.builder()
                        .description("必填。按顺序执行的候选操作队列。底层仍然一次执行一条 MCP 指令，单条失败可由桥接层记录后跳过；等待时传空数组")
                        .items(operationSchema)
                        .build())
                .required("status", "summary", "operations")
                .additionalProperties(false)
                .build();

        return ToolSpecification.builder()
                .name(ENQUEUE_TOOL_NAME)
                .description("""
                        提交游戏操作队列。不要直接调用底层 MCP 工具；把多个操作按顺序放进 operations。
                        桥接层会负责执行前修复、逐条调用 MCP、执行后刷新状态和中断检测。
                        """)
                .parameters(parameters)
                .build();
    }
}
