package p1.component.agent.gamer;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import p1.component.agent.gamer.bridge.GameBridgeActionStatus;
import p1.component.agent.gamer.bridge.GameBridgeService;
import p1.component.agent.gamer.bridge.GameOperationQueueProcessor;
import p1.component.agent.gamer.memory.TurnScopedGamerChatMemory;
import p1.config.mcp.GamerProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 管理游戏会话中游戏智能体的生命周期。
 * <p>
 * 为每个会话构建 GamerAgent 实例，并注入桥接后的游戏工具。
 */
@Service
@Slf4j
public class GamerAgentService {

    private final GamerMCPClientFactory mcpClientFactory;
    private final GameBridgeService bridgeService;
    private final ChatModel chatModel;
    private final GamerStreamingAgentService streamingAgentService;
    private final GamerProperties gamerProperties;

    /**
     * 创建游戏智能体服务。
     *
     * @param mcpClientFactory   MCP 客户端工厂
     * @param bridgeService      游戏桥接服务
     * @param chatModel          游戏智能体使用的模型
     */
    public GamerAgentService(GamerMCPClientFactory mcpClientFactory,
                             GameBridgeService bridgeService,
                             @Qualifier("gamerChatModel") ChatModel chatModel,
                             GamerStreamingAgentService streamingAgentService,
                             GamerProperties gamerProperties) {
        this.mcpClientFactory = mcpClientFactory;
        this.bridgeService = bridgeService;
        this.chatModel = chatModel;
        this.streamingAgentService = streamingAgentService;
        this.gamerProperties = gamerProperties;
    }

    /**
     * 每个会话一个锁，防止游戏循环和用户指令并发操作同一会话。
     */
    private final Map<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    /**
     * 获取会话级互斥锁。
     *
     * @param sessionId LangChain4j 的会话记忆 id
     * @return 该会话独占的锁对象
     */
    private ReentrantLock getSessionLock(String sessionId) {
        return sessionLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
    }

    /**
     * 构建绑定到指定游戏 MCP 工具的 GamerAgent。
     *
     * @param gameName  游戏名
     * @param sessionId 用户侧会话 id
     * @return 已绑定桥接工具和通知工具的 GamerAgent
     */
    public GamerAgent buildAgent(String gameName, String sessionId) {
        if (!mcpClientFactory.isGameConfigured(gameName)) {
            throw new IllegalArgumentException("游戏未配置 MCP: " + gameName);
        }

        // agent 只能看到桥接后的虚拟批量工具，不能直接看到底层 MCP 操作工具。
        ToolProvider mcpTools = bridgeService.bridgedToolProvider(gameName);

        GamerAgent agent = AiServices.builder(GamerAgent.class)
                .chatModel(chatModel)
                // 只保留当前调用内的消息，避免完整游戏状态跨轮进入聊天记忆。
                .chatMemoryProvider(TurnScopedGamerChatMemory::new)
                .toolProvider(mcpTools)
                .build();

        log.debug("[游戏智能体] Agent 已创建: game={}, session={}", gameName, sessionId);
        return agent;
    }

    /**
     * 通过Gamer Agent处理用户消息。
     * <p>
     * Gamer Agent 的操作会通过tool call转发到{@link GameOperationQueueProcessor#enqueueAndDrain}。
     *
     * @param gameName    游戏名
     * @param sessionId   用户侧会话 id
     * @param userMessage 外层传入的用户指令
     * @return 游戏智能体或桥接工具返回的文本
     */
    public String play(String gameName, String sessionId, String userMessage) {
        String memoryId = GameSessionKey.of(gameName, sessionId);
        ReentrantLock lock = getSessionLock(memoryId);
        lock.lock();
        String previousSessionId = MDC.get("sessionId");
        String previousServiceInfo = MDC.get("serviceInfo");
        try {
            // gamer 使用单独 memoryId 写入 MDC，方便日志和 reasoning_content 暂存按游戏会话归档。
            MDC.put("sessionId", memoryId);
            MDC.put("serviceInfo", "gamer");

            if (gamerProperties.getStreaming().isEnabled()) {
                return streamingAgentService.play(gameName, sessionId, userMessage);
            }

            return playWithToolCalling(gameName, sessionId, userMessage, memoryId);
        } finally {
            // 同一游戏会话必须串行执行，避免用户指令和定时循环同时操作 MCP。
            restoreMdc("sessionId", previousSessionId);
            restoreMdc("serviceInfo", previousServiceInfo);
            lock.unlock();
        }
    }

    /**
     * 使用原 LangChain4j tool calling 路径处理 gamer 决策。
     *
     * @param gameName    游戏名
     * @param sessionId   用户侧会话 id
     * @param userMessage 外层传入的用户指令
     * @param memoryId    gamer 统一会话 key
     * @return 模型或工具返回文本
     */
    private String playWithToolCalling(String gameName, String sessionId, String userMessage, String memoryId) {
        // 每次调用前重新构建上下文，保证状态和可用工具都是最新的。
        GamerAgent agent = buildAgent(gameName, sessionId);
        String displayName = mcpClientFactory.getGameDisplayName(gameName);
        String gameGuidelines = mcpClientFactory.getGameGuidelines(gameName);
        String context = bridgeService.buildAgentContext(gameName, sessionId, userMessage);
        Result<String> result = agent.play(memoryId, context, displayName, gameGuidelines);
        return result == null || result.content() == null ? "" : result.content();
    }

    /**
     * 恢复进入 gamer 调用前的 MDC 字段。
     *
     * @param key      MDC 字段名
     * @param oldValue 进入 gamer 调用前的字段值
     */
    private void restoreMdc(String key, String oldValue) {
        if (oldValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, oldValue);
    }

    /**
     * 查询本轮游戏智能体通过桥接层提交的结构化动作状态。
     *
     * @param gameName  游戏名
     * @param sessionId 用户侧会话 id
     * @return 最近一次 enqueue_operations.status；本轮没有提交时返回 UNKNOWN
     */
    public GameBridgeActionStatus lastActionStatus(String gameName, String sessionId) {
        return bridgeService.lastActionStatus(gameName, sessionId);
    }
}
