package p1.component.agent.gamer.bridge;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p1.component.agent.gamer.GameSessionKey;
import p1.component.agent.gamer.GamerMCPClientFactory;
import p1.component.agent.gamer.adapter.GameAdapter;
import p1.component.agent.gamer.adapter.GameAdapterContext;
import p1.component.agent.gamer.adapter.GameAdapterRegistry;
import p1.component.agent.gamer.adapter.GameStateSnapshot;
import p1.component.agent.gamer.memory.GamerWorkingMemoryService;
import p1.config.mcp.MCPProperties;

/**
 * gameAgent和MCP Server的桥接服务。
 * <p>
 * 该服务负责把真实 MCP 工具包装成一个虚拟批量工具，并在每次 agent 决策前注入最新游戏状态。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameBridgeService {

    private final GamerMCPClientFactory mcpClientFactory;
    private final MCPProperties mcpProperties;
    private final GameAdapterRegistry adapterRegistry;
    private final GameOperationQueueProcessor queueProcessor;
    private final GamerWorkingMemoryService workingMemoryService;

    /**
     * 构建暴露给 gamer agent 的桥接工具集合。
     *
     * @param gameName 游戏名
     * @return 只暴露 enqueue_operations 的 ToolProvider
     */
    public ToolProvider bridgedToolProvider(String gameName) {
        MCPProperties.GameMCPConfig config = requireConfig(gameName);
        GameAdapter adapter = adapterRegistry.getAdapter(gameName, config);
        ToolProvider rawProvider = mcpClientFactory.getToolProvider(gameName);
        return new GameBridgeToolProvider(gameName, config, adapter, rawProvider, queueProcessor);
    }

    /**
     * 构建注入给 agent 的上下文。
     *
     * @param gameName    游戏名
     * @param sessionId   用户侧会话 id
     * @param userMessage 外层调用传入的用户指令
     * @return 包含桥接规则、最新状态、可用操作和用户指令的文本
     */
    public String buildAgentContext(String gameName, String sessionId, String userMessage) {
        String memoryId = GameSessionKey.of(gameName, sessionId);
        MCPProperties.GameMCPConfig config = requireConfig(gameName);
        GameAdapter adapter = adapterRegistry.getAdapter(gameName, config);
        ToolProvider rawProvider = mcpClientFactory.getToolProvider(gameName);

        ToolProviderResult tools = rawProvider.provideTools(new ToolProviderRequest(memoryId, UserMessage.from(userMessage)));

        // 每次构建新上下文前清掉上一轮状态，避免本轮 agent 没有调用队列工具时读到旧结果。
        queueProcessor.clearLastStatus(memoryId);

        // 状态只由桥接层主动获取，并直接注入给 agent；agent 不需要也不应该调用状态工具。
        GameStateSnapshot state = adapter.fetchState(new GameAdapterContext(gameName, memoryId, tools, config));
        queueProcessor.rememberPlanningState(memoryId, state);
        workingMemoryService.observeState(gameName, memoryId, state);

        // 上一轮队列中断原因会随最新状态一并注入，要求 agent 放弃旧计划重新决策。
        String notice = queueProcessor.consumeNotice(memoryId);
        String workingMemory = workingMemoryService.renderMemory(gameName, memoryId);
        StringBuilder sb = new StringBuilder();
        sb.append("<bridge_rules>\n")
                .append("- 系统已经注入最新游戏状态，不要调用任何状态查询工具。\n")
                .append("- 游戏操作必须通过 `").append(GameBridgeToolProvider.ENQUEUE_TOOL_NAME).append("` 一次提交一个操作队列。\n")
                .append("- operations 中的每一项使用下方列出的 MCP 工具名和参数；桥接层会逐条执行。\n")
                .append("- 如果桥接层报告队列中断，立即基于最新状态重新决策，不要沿用旧队列。\n")
                .append("- gamer_memory 只是历史决策摘要，不能覆盖 latest_game_state 中的当前事实。\n")
                .append("</bridge_rules>\n\n");
        if (notice != null && !notice.isBlank()) {
            sb.append("<bridge_notice>\n").append(notice).append("\n</bridge_notice>\n\n");
        }
        sb.append("<gamer_memory>\n")
                .append(workingMemory)
                .append("\n</gamer_memory>\n\n")
                .append("<latest_game_state>\n")
                .append(adapter.renderStateForAgent(state))
                .append("\n</latest_game_state>\n\n")
                .append("<available_operations>\n")
                .append(adapter.renderAvailableOperations(tools, config))
                .append("</available_operations>\n\n")
                .append("<user_instruction>\n")
                .append(userMessage == null ? "" : userMessage)
                .append("\n</user_instruction>");
        return sb.toString();
    }

    /**
     * 查询本轮 agent 是否通过桥接工具提交了结构化状态。
     *
     * @param gameName  游戏名
     * @param sessionId 用户侧会话 id
     * @return 最近一次 enqueue_operations.status；本轮未提交时返回 UNKNOWN
     */
    public GameBridgeActionStatus lastActionStatus(String gameName, String sessionId) {
        return queueProcessor.lastStatus(GameSessionKey.of(gameName, sessionId));
    }

    /**
     * 获取并校验游戏 MCP 配置。
     *
     * @param gameName 游戏名
     * @return 已启用的游戏 MCP 配置
     */
    private MCPProperties.GameMCPConfig requireConfig(String gameName) {
        MCPProperties.GameMCPConfig config = mcpProperties.getGames().get(gameName);
        if (config == null || !config.isEnabled()) {
            throw new IllegalArgumentException("游戏未配置 MCP: " + gameName);
        }
        return config;
    }
}
