package p1.component.gamer;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import p1.component.gamer.bridge.GameBridgeActionStatus;
import p1.component.gamer.bridge.GameBridgeService;
import p1.component.gamer.bridge.GameOperationQueueProcessor;
import p1.component.gamer.bridge.GamerRPBridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 管理游戏会话中游戏智能体的生命周期。
 * <p>
 * 为每个会话构建 GamerAgent 实例，注入桥接后的游戏工具，
 * 以及用于向角色扮演智能体传递事件的 notifyRP 工具。
 */
@Service
@Slf4j
public class GamerAgentService {

    private final GamerMCPClientFactory mcpClientFactory;
    private final GameBridgeService bridgeService;
    private final GamerRPNotifyTool notifyTool;
    private final GamerRPBridge bridge;
    private final ChatMemoryProvider chatMemoryProvider;
    private final ChatModel chatModel;

    /**
     * 创建游戏智能体服务。
     *
     * @param mcpClientFactory   MCP 客户端工厂
     * @param bridgeService      游戏桥接服务
     * @param notifyTool         RP 通知工具
     * @param bridge             RP 事件桥
     * @param chatMemoryProvider 聊天记忆提供器
     * @param chatModel          游戏智能体使用的模型
     */
    public GamerAgentService(GamerMCPClientFactory mcpClientFactory,
                             GameBridgeService bridgeService,
                             GamerRPNotifyTool notifyTool,
                             GamerRPBridge bridge,
                             ChatMemoryProvider chatMemoryProvider,
                             @Qualifier("gamerChatModel") ChatModel chatModel) {
        this.mcpClientFactory = mcpClientFactory;
        this.bridgeService = bridgeService;
        this.notifyTool = notifyTool;
        this.bridge = bridge;
        this.chatMemoryProvider = chatMemoryProvider;
        this.chatModel = chatModel;
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
                .chatMemoryProvider(chatMemoryProvider)
                .toolProvider(mcpTools)
                .tools(notifyTool)
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
        try {
            // 每次调用前重新构建上下文，保证状态和可用工具都是最新的。
            GamerAgent agent = buildAgent(gameName, sessionId);
            String displayName = mcpClientFactory.getGameDisplayName(gameName);
            String gameGuidelines = mcpClientFactory.getGameGuidelines(gameName);
            String context = bridgeService.buildAgentContext(gameName, sessionId, userMessage);
            Result<String> result = agent.play(memoryId, context, displayName, gameGuidelines);
            return result == null || result.content() == null ? "" : result.content();
        } finally {
            // 同一游戏会话必须串行执行，避免用户指令和定时循环同时操作 MCP。
            lock.unlock();
        }
    }

    /**
     * 提取待处理的游戏事件，供角色扮演智能体进行角色化叙述。
     *
     * @param gameName  游戏名
     * @param sessionId 用户侧会话 id
     * @return 可注入给角色扮演智能体的事件文本；没有事件时返回 null
     */
    public String drainGameEvents(String gameName, String sessionId) {
        return bridge.drainEventsForRP(GameSessionKey.of(gameName, sessionId));
    }

    /**
     * 查询当前会话是否有待处理的 RP 事件。
     *
     * @param gameName  游戏名
     * @param sessionId 用户侧会话 id
     * @return true 表示存在待叙述事件
     */
    public boolean hasPendingEvents(String gameName, String sessionId) {
        return bridge.hasEvents(GameSessionKey.of(gameName, sessionId));
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
