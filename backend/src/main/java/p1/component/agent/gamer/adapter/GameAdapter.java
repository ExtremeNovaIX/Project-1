package p1.component.agent.gamer.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import p1.config.mcp.MCPProperties;

import java.util.ArrayDeque;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 游戏适配器接口。
 * <p>
 * 通用桥接层负责排队、逐条执行和转发 MCP 工具；具体游戏适配器负责解释游戏语义，
 * 例如如何获取状态、如何修复过期操作、以及什么时候应该中断剩余队列。
 */
public interface GameAdapter {

    /**
     * 返回适配器 id。
     *
     * @return 配置中 mcp.games.*.adapter 使用的适配器标识
     */
    String id();

    /**
     * 判断该适配器是否支持当前游戏配置。
     *
     * @param gameName 游戏名
     * @param config   游戏 MCP 配置
     * @return true 表示当前适配器可用于该游戏
     */
    default boolean supports(String gameName, MCPProperties.GameMCPConfig config) {
        return id().equalsIgnoreCase(config.getAdapter());
    }

    /**
     * 构建状态工具参数。子类可覆写以传入特定参数（如 format=json）。
     *
     * @param context 适配器运行上下文
     * @return 状态工具参数 JSON
     */
    default String stateToolArguments(GameAdapterContext context) {
        return "{}";
    }

    /**
     * 解析 MCP 返回的状态 JSON。
     *
     * @param raw MCP 状态工具返回的原始文本
     * @return 标准化状态快照
     */
    default GameStateSnapshot parseState(String raw) {
        try {
            JsonNode json = new ObjectMapper().readTree(raw);
            String stateType = json.path("state_type").asText("");
            return new GameStateSnapshot(raw, json, stateType);
        } catch (Exception e) {
            String preview = raw == null ? "null" : raw.substring(0, Math.min(raw.length(), 500));
            throw new GameBridgeException("解析游戏状态失败，MCP 返回: " + preview, e);
        }
    }

    /**
     * 解析状态工具在 MCP 工具列表中的实际名称。
     * 子类可覆写以处理前缀（如 STS2 多人模式 mp_ 前缀）。
     *
     * @param config 游戏 MCP 配置
     * @return MCP 工具列表中的实际状态工具名
     */
    default String resolveStateToolName(MCPProperties.GameMCPConfig config) {
        return config.getStateToolName();
    }

    /**
     * 从底层 MCP 获取最新游戏状态。
     *
     * @param context 适配器运行上下文
     * @return 标准化后的游戏状态快照
     */
    default GameStateSnapshot fetchState(GameAdapterContext context) {
        String name = resolveStateToolName(context.config());
        ToolExecutor executor = context.tools().toolExecutorByName(name);
        if (executor == null) {
            String availableTools = context.tools().tools().keySet().stream()
                    .map(ToolSpecification::name)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.joining(", "));
            throw new GameBridgeException("状态工具不存在: " + name
                    + (availableTools.isBlank() ? "" : "；可用工具: " + availableTools));
        }
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(name)
                .arguments(stateToolArguments(context))
                .build();
        String raw = executor.execute(request, context.sessionId());
        return parseState(raw);
    }

    /**
     * 将游戏状态渲染为注入给 agent 的文本。
     *
     * @param state 游戏状态快照
     * @return 适合放入 prompt 的状态文本
     */
    default String renderStateForAgent(GameStateSnapshot state) {
        return state.rawJson();
    }

    // ── 操作队列钩子（含合理默认实现） ──

    /**
     * 在操作入队前记录游戏语义信息。
     *
     * @param operation    agent 提交的原始操作
     * @param plannedState agent 做计划时看到的状态
     * @return 带有元数据的排队操作
     */
    default QueuedGameOperation prepareOperation(GameOperation operation, GameStateSnapshot plannedState) {
        return QueuedGameOperation.from(operation, plannedState);
    }

    /**
     * 将一批操作全部入队。子类可覆写以跨操作模拟状态变化（如手牌减少），
     * 确保每条操作的元数据（plannedCardName 等）基于前序操作执行后的预期状态。
     *
     * @param operations   agent 提交的原始操作列表
     * @param plannedState agent 做计划时看到的状态
     * @return 带有元数据的排队操作队列
     */
    default ArrayDeque<QueuedGameOperation> prepareBatch(List<GameOperation> operations, GameStateSnapshot plannedState) {
        ArrayDeque<QueuedGameOperation> queue = new ArrayDeque<>();
        for (GameOperation op : operations) {
            queue.offerLast(prepareOperation(op, plannedState));
        }
        return queue;
    }

    /**
     * 在操作真正执行前进行修复。
     *
     * @param operation    即将执行的排队操作
     * @param currentState 执行前最新状态
     * @return 修复后的 MCP 工具请求
     */
    default ToolExecutionRequest repairBeforeExecute(QueuedGameOperation operation, GameStateSnapshot currentState) {
        return operation.request();
    }

    /**
     * 在操作执行后判断剩余队列是否仍然可靠。
     * 默认不做任何操作（继续执行剩余队列）。
     *
     * @param operation   已执行的操作
     * @param beforeState 执行前状态
     * @param afterState  执行后状态
     * @param toolResult  MCP 工具返回文本
     * @param hasRemainingOperations true 表示当前队列后面还有未执行操作
     * @throws GameBridgeException 剩余队列不可靠，应当丢弃
     */
    default void monitorAfterExecute(QueuedGameOperation operation,
                                     GameStateSnapshot beforeState,
                                     GameStateSnapshot afterState,
                                     String toolResult,
                                     boolean hasRemainingOperations) {
    }

    /**
     * 判断某个 MCP 工具是否是状态查询工具。
     *
     * @param toolName MCP 工具名
     * @param config   游戏 MCP 配置
     * @return true 表示该工具应由桥接层内部使用，不暴露给 agent 作为操作工具
     */
    default boolean isStateTool(String toolName, MCPProperties.GameMCPConfig config) {
        return toolName != null && toolName.equals(resolveStateToolName(config));
    }

    /**
     * 渲染可供 agent 提交到 operations 的 MCP 操作工具列表。
     *
     * @param tools  底层 MCP 工具集合
     * @param config 游戏 MCP 配置
     * @return 可用操作工具的说明文本
     */
    default String renderAvailableOperations(ToolProviderResult tools, MCPProperties.GameMCPConfig config) {
        StringBuilder sb = new StringBuilder();
        tools.tools().keySet().stream()
                .filter(spec -> !isStateTool(spec.name(), config))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .forEach(spec -> sb.append("- ")
                        .append(spec.name())
                        .append(": ")
                        .append(spec.description() == null ? "" : spec.description())
                        .append("\n"));
        return sb.isEmpty() ? "(没有可用操作工具)" : sb.toString();
    }
}
