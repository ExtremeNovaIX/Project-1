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
import p1.component.agent.gamer.adapter.*;
import p1.component.agent.gamer.memory.GamerWorkingMemoryService;
import p1.config.mcp.GamerProperties;
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
    private final GamerProperties gamerProperties;

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

        // 把用户当前指令写入工作记忆，供 gamer agent 在决策时参考。
        workingMemoryService.recordUserMessage(gameName, memoryId, userMessage);

        // 上一轮队列中断原因会随最新状态一并注入，要求 agent 放弃旧计划重新决策。
        String notice = queueProcessor.consumeNotice(memoryId);
        String lastActionResult = queueProcessor.peekLastActionResult(memoryId);
        String workingMemory = workingMemoryService.renderMemory(gameName, memoryId);
        StringBuilder sb = new StringBuilder();
        sb.append("<bridge_rules>\n")
                .append("- 系统已经注入最新游戏状态，不要调用任何状态查询工具。\n")
                .append("- latest_game_state 是游戏 MCP 返回的源 JSON；优先直接读取其中的原生字段。\n")
                .append("- 游戏操作必须通过 `").append(GameBridgeToolProvider.ENQUEUE_TOOL_NAME).append("` 一次提交一个候选操作队列。\n")
                .append("- operations 中的每一项使用下方列出的 MCP 工具名和参数；桥接层会逐条执行。\n")
                .append("- MCP 业务失败后，如果最新状态仍可行动，桥接层会记录错误、跳过失败操作并继续剩余队列。\n")
                .append("- state_type 变化、进入新界面、抽牌/弃牌导致手牌不可预测变化时，桥接层会中断队列并丢弃剩余操作。\n")
                .append("- 如果桥接层报告队列中断，立即基于 latest_game_state 重新决策，不要沿用旧队列。\n")
                .append("- 抽牌、弃牌、随机、领取奖励、打开选择界面等会改变行动窗口的操作应放在队列末尾。\n")
                .append("- gamer_memory 只是历史决策摘要，不能覆盖 latest_game_state 中的当前事实。\n")
                .append("</bridge_rules>\n\n");
        if (gamerProperties.getStreaming().isEnabled()) {
            sb.append("<streaming_output_rules>\n")
                    .append("- 当前启用流式 response JSON ACTION 模式；系统会从普通 response 流解析 JSON。\n")
                    .append("- 不要依赖 thinking/reasoning_content；第一段尽量直接输出 JSON 对象。\n")
                    .append("- 每个 JSON 对象必须包含 operations；同一稳定行动窗口内尽量一次提交完整确定队列。\n")
                    .append("- 抽牌、弃牌、随机生成、打开选择界面、确认选择、领取奖励等会改变状态的操作必须放在当前 JSON 的最后。\n")
                    .append("- JSON 输出后可以继续输出下一个 JSON；不要使用 Markdown 代码块。\n")
                    .append("- 示例只展示格式，实际 tool/args 必须来自 available_operations/latest_game_state：\n")
                    .append("{\"type\":\"action\",\"status\":\"CONTINUE\",\"summary\":\"先执行确定收益操作\",\"operations\":[{\"tool\":\"combat_play_card\",\"args\":{\"card\":\"痛击\",\"target\":\"ENEMY_0\"},\"note\":\"先上易伤\"}]}\n")
                    .append("</streaming_output_rules>\n\n");
        }
        if (notice != null && !notice.isBlank()) {
            sb.append("<bridge_notice>\n").append(notice).append("\n</bridge_notice>\n\n");
        }
        if (lastActionResult != null && !lastActionResult.isBlank()) {
            sb.append("<last_action_result>\n")
                    .append(lastActionResult)
                    .append("\n</last_action_result>\n\n");
        }
        sb.append("<gamer_memory>\n")
                .append(workingMemory)
                .append("\n</gamer_memory>\n\n")
                .append("<latest_game_state>\n")
                .append(adapter.renderStateForAgent(state))
                .append("\n</latest_game_state>\n\n")
                .append("<available_operations>\n")
                .append(adapter.renderAvailableOperations(tools, config, state))
                .append("</available_operations>\n\n")
                .append("<user_instruction>\n")
                .append(userMessage == null ? "" : userMessage)
                .append("\n</user_instruction>");
        return sb.toString();
    }

    /**
     * 执行流式解析得到的操作队列。
     * <p>
     * 流式路径不经过 LangChain4j tool calling，但仍复用同一个队列处理器，
     * 因此修复、软错误、状态监视和中断逻辑保持一致。
     *
     * @param gameName     游戏名
     * @param sessionId    用户侧会话 id
     * @param rawArguments enqueue_operations 兼容 JSON 参数
     * @return 队列处理器返回的执行结果文本
     */
    public String executeOperationQueue(String gameName, String sessionId, String rawArguments) {
        String memoryId = GameSessionKey.of(gameName, sessionId);
        MCPProperties.GameMCPConfig config = requireConfig(gameName);
        GameAdapter adapter = adapterRegistry.getAdapter(gameName, config);
        ToolProvider rawProvider = mcpClientFactory.getToolProvider(gameName);
        ToolProviderResult tools = rawProvider.provideTools(new ToolProviderRequest(memoryId, UserMessage.from("streaming operation dispatch")));
        return queueProcessor.enqueueAndDrain(gameName, memoryId, adapter, config, tools, rawArguments);
    }

    /**
     * 探测当前游戏会话是否需要 agent 行动。
     * <p>
     * 桥接层统一负责获取最新 MCP 状态；适配器只负责解释这份状态是否可行动。
     *
     * @param gameName  游戏名
     * @param sessionId 用户侧会话 id
     * @return 当前行动窗口判断结果
     */
    public GameActionability probeActionability(String gameName, String sessionId) {
        String memoryId = GameSessionKey.of(gameName, sessionId);
        MCPProperties.GameMCPConfig config = requireConfig(gameName);
        GameAdapter adapter = adapterRegistry.getAdapter(gameName, config);
        ToolProvider rawProvider = mcpClientFactory.getToolProvider(gameName);
        ToolProviderResult tools = rawProvider.provideTools(new ToolProviderRequest(memoryId, UserMessage.from("probe game actionability")));
        GameStateSnapshot state = adapter.fetchState(new GameAdapterContext(gameName, memoryId, tools, config));
        return adapter.evaluateActionability(state);
    }

    /**
     * 查询本轮 agent 是否通过桥接工具提交了结构化状态。
     *
     * @param gameName  游戏名
     * @param sessionId 用户侧会话 id
     * @return 最近一次 enqueue_operations.status；本轮未提交时返回 UNKNOWN
     */
    /**
     * 查询最近一次 enqueue_operations 中模型生成的用户可见消息。
     *
     * @param gameName  游戏名
     * @param sessionId 用户侧会话 id
     * @return 用户可见消息；本轮未生成时返回空字符串
     */
    public String lastUserMessage(String gameName, String sessionId) {
        return queueProcessor.lastMessage(GameSessionKey.of(gameName, sessionId));
    }

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
