package p1.component.gamer.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p1.component.gamer.adapter.GameAdapter;
import p1.component.gamer.adapter.GameAdapterContext;
import p1.component.gamer.adapter.GameBridgeException;

import p1.component.gamer.adapter.GameOperation;
import p1.component.gamer.adapter.GameStateSnapshot;
import p1.component.gamer.adapter.QueuedGameOperation;
import p1.config.mcp.MCPProperties;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏操作队列处理器。
 * <p>
 * 该组件位于 gamer agent 和 MCP 工具之间：接收模型一次性提交的多条操作，
 * 再按顺序逐条调用底层 MCP 工具，并在每条操作前后交给 GameAdapter 做修复和监视。
 */
@Component
@Slf4j
public class GameOperationQueueProcessor {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, GameStateSnapshot> planningStates = new ConcurrentHashMap<>();
    private final Map<String, String> notices = new ConcurrentHashMap<>();
    private final Map<String, GameBridgeActionStatus> lastStatuses = new ConcurrentHashMap<>();

    /**
     * 记录本轮 agent 决策时看到的状态。
     *
     * @param memoryId LangChain4j 的会话记忆 id
     * @param state    注入给 agent 的最新游戏状态
     */
    public void rememberPlanningState(String memoryId, GameStateSnapshot state) {
        planningStates.put(memoryId, state);
    }

    /**
     * 取出上一轮桥接层中断或修复失败的提示。
     *
     * @param memoryId LangChain4j 的会话记忆 id
     * @return 需要注入给 agent 的桥接层提示；没有提示时返回 null
     */
    public String consumeNotice(String memoryId) {
        return notices.remove(memoryId);
    }

    /**
     * 清空本轮动作状态，避免 agent 未调用队列工具时读到上一轮结果。
     *
     * @param memoryId LangChain4j 的会话记忆 id
     */
    public void clearLastStatus(String memoryId) {
        lastStatuses.remove(memoryId);
    }

    /**
     * 查询最近一次队列工具提交的结构化状态。
     *
     * @param memoryId LangChain4j 的会话记忆 id
     * @return 最近一次队列状态；如果本轮没有调用队列工具则返回 UNKNOWN
     */
    public GameBridgeActionStatus lastStatus(String memoryId) {
        return lastStatuses.getOrDefault(memoryId, GameBridgeActionStatus.UNKNOWN);
    }

    /**
     * 接收模型提交的操作队列并执行。
     *
     * @param gameName     游戏名
     * @param memoryId     LangChain4j 的会话记忆 id
     * @param adapter      当前游戏使用的适配器
     * @param config       当前游戏的 MCP 配置
     * @param rawTools     底层 MCP 工具集合
     * @param rawArguments enqueue_operations 的 JSON 参数
     * @return 返回给 LangChain4j 的工具执行结果文本
     */
    public String enqueueAndDrain(String gameName,
                                  String memoryId,
                                  GameAdapter adapter,
                                  MCPProperties.GameMCPConfig config,
                                  ToolProviderResult rawTools,
                                  String rawArguments) {
        String key = memoryId == null || memoryId.isBlank() ? gameName : memoryId;
        GameAdapterContext context = new GameAdapterContext(gameName, key, rawTools, config);
        try {
            // 解析虚拟工具参数，后续所有执行都基于这个批次的 operations。
            JsonNode root = objectMapper.readTree(rawArguments == null || rawArguments.isBlank() ? "{}" : rawArguments);
            GameBridgeActionStatus requestedStatus = GameBridgeActionStatus.from(root.path("status").asText("CONTINUE"));
            String summary = root.path("summary").asText("");

            List<GameOperation> operations = parseOperations(root.path("operations"));
            if (operations.isEmpty()) {
                lastStatuses.put(key, requestedStatus);
                return formatStatus(requestedStatus, "未提交操作。" + summary);
            }
            log.info("[游戏桥接] 收到操作队列: game={}, memoryId={}, operations={}", gameName, key, operations);

            // plannedState 是 agent 做计划时看到的状态，用于记录“原本想操作哪张牌”等语义信息。
            GameStateSnapshot plannedState = planningStates.get(key);
            if (plannedState == null) {
                plannedState = adapter.fetchState(context);
            }
            // 队列只在本次 enqueue_operations 调用内存在；中断后必须自然丢弃剩余操作。
            ArrayDeque<QueuedGameOperation> queue = new ArrayDeque<>();
            for (GameOperation operation : operations) {
                queue.offerLast(adapter.prepareOperation(operation, plannedState));
            }

            // drainQueue 内部仍然逐条调用 MCP，任何修复失败或状态异常都会抛异常中断。
            GameStateSnapshot latestState = drainQueue(key, adapter, context, queue, plannedState);
            planningStates.put(key, latestState);
            lastStatuses.put(key, requestedStatus);
            return formatStatus(requestedStatus, "已执行 " + operations.size() + " 条操作。 " + summary);
        } catch (GameBridgeException e) {
            log.warn("[游戏桥接] 操作队列中断: game={}, memoryId={}, executed={}, reason={}", gameName, key, e.getExecutedCount(), e.getMessage());
            lastStatuses.put(key, GameBridgeActionStatus.INTERRUPTED);
            notices.put(key, e.getMessage());
            return "[CONTINUE] 操作队列已中断，剩余指令已丢弃。" + e.getMessage() + "\n请基于下一次注入的最新状态重新决策。";
        } catch (Exception e) {
            log.error("[游戏桥接] 操作队列处理失败: game={}, memoryId={}", gameName, key, e);
            lastStatuses.put(key, GameBridgeActionStatus.CONTINUE);
            notices.put(key, "桥接层处理失败: " + e.getMessage());
            return "[CONTINUE] 桥接层处理操作队列失败，已丢弃队列。原因: " + e.getMessage() + "\n请基于下一次注入的最新状态重新决策。";
        }
    }

    /**
     * 执行队列中的所有操作。
     *
     * @param key          会话 key
     * @param adapter      当前游戏适配器
     * @param context      适配器运行上下文
     * @param queue        待执行操作队列
     * @param initialState agent 规划时的初始状态
     * @return 全部操作执行后的最新状态
     * @throws GameBridgeException 修复失败、工具不存在、执行异常或监视中断时抛出
     */
    private GameStateSnapshot drainQueue(String key,
                                         GameAdapter adapter,
                                         GameAdapterContext context,
                                         ArrayDeque<QueuedGameOperation> queue,
                                         GameStateSnapshot initialState) {
        int executed = 0;
        GameStateSnapshot currentState = initialState;
        while (!queue.isEmpty()) {
            QueuedGameOperation operation = queue.pollFirst();
            GameStateSnapshot beforeState = currentState != null ? currentState : adapter.fetchState(context);
            ToolExecutionRequest request;
            try {
                request = adapter.repairBeforeExecute(operation, beforeState);
            } catch (GameBridgeException e) {
                throw new GameBridgeException("adapter执行指令修复失败: " + e.getMessage() + "\n最新状态:\n" + adapter.renderStateForAgent(beforeState), executed);
            }

            ToolExecutor executor = context.tools().toolExecutorByName(request.name());
            if (executor == null) {
                throw new GameBridgeException("MCP 工具不存在: " + request.name(), executed);
            }

            String toolResult;
            try {
                toolResult = executor.execute(request, key);
            } catch (Exception e) {
                throw new GameBridgeException("MCP 工具执行失败: " + request.name() + "，原因: " + e.getMessage(), executed);
            }
            executed++;

            QueuedGameOperation executedOperation = operation.withRequest(request);
            GameStateSnapshot afterState = fetchStateUntilMonitorPasses(
                    key, adapter, context, executedOperation, beforeState, toolResult, !queue.isEmpty(), executed);
            currentState = afterState;
        }
        return currentState;
    }

    /**
     * 获取操作后的状态，并在疑似读到过渡态时延迟重读。
     *
     * @param key                    会话 key
     * @param adapter                当前游戏适配器
     * @param context                适配器运行上下文
     * @param operation              已执行的操作
     * @param beforeState            执行前状态
     * @param toolResult             MCP 工具返回文本
     * @param hasRemainingOperations 当前队列是否仍有后续操作
     * @param executed               当前批次已执行操作数
     * @return 通过监视检查的最新状态
     */
    private GameStateSnapshot fetchStateUntilMonitorPasses(String key,
                                                           GameAdapter adapter,
                                                           GameAdapterContext context,
                                                           QueuedGameOperation operation,
                                                           GameStateSnapshot beforeState,
                                                           String toolResult,
                                                           boolean hasRemainingOperations,
                                                           int executed) {
        int maxAttempts = Math.max(1, context.config().getStateSettleMaxAttempts());
        long delayMs = Math.max(0, context.config().getStateSettleDelayMs());
        GameBridgeException lastFailure = null;
        GameStateSnapshot afterState = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                sleepBeforeStateRetry(delayMs, key, attempt, maxAttempts);
            }

            afterState = adapter.fetchState(context);
            try {
                adapter.monitorAfterExecute(operation, beforeState, afterState, toolResult, hasRemainingOperations);
                if (attempt > 1) {
                    log.info("[游戏桥接] 状态延迟重读后通过监视: memoryId={}, attempt={}/{}",
                            key, attempt, maxAttempts);
                }
                return afterState;
            } catch (GameBridgeException e) {
                if (!shouldRetryMonitorFailure(e) || attempt >= maxAttempts) {
                    throw new GameBridgeException(
                            "状态监视触发中断: " + e.getMessage()
                                    + "\n最新状态:\n" + adapter.renderStateForAgent(afterState),
                            executed);
                }
                lastFailure = e;
                log.debug("[游戏桥接] 状态监视未通过，疑似读取到过渡态，准备延迟重读: memoryId={}, attempt={}/{}, reason={}",
                        key, attempt, maxAttempts, e.getMessage());
            }
        }

        throw new GameBridgeException(
                "状态监视触发中断: " + (lastFailure == null ? "未知状态同步问题" : lastFailure.getMessage())
                        + "\n最新状态:\n" + (afterState == null ? "(未能获取状态)" : adapter.renderStateForAgent(afterState)),
                executed);
    }

    /**
     * 判断监视失败是否值得重读确认。
     *
     * @param failure 适配器抛出的监视异常
     * @return true 表示可能是状态刷新延迟，可以短暂等待后重读
     */
    private boolean shouldRetryMonitorFailure(GameBridgeException failure) {
        String message = failure.getMessage();
        return message == null || !message.startsWith("MCP 工具执行失败:");
    }

    /**
     * 在状态重读前等待一小段时间。
     *
     * @param delayMs     等待时间（毫秒）
     * @param key         会话 key
     * @param attempt     当前重读次数
     * @param maxAttempts 最大重读次数
     */
    private void sleepBeforeStateRetry(long delayMs, String key, int attempt, int maxAttempts) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GameBridgeException("状态重读等待被中断: memoryId=" + key + ", attempt=" + attempt + "/" + maxAttempts, e);
        }
    }

    /**
     * 将 enqueue_operations.operations 解析成内部操作对象。
     *
     * @param node operations 字段，可以是数组，也可以是字符串形式的 JSON 数组
     * @return 按模型声明顺序排列的操作列表
     */
    private List<GameOperation> parseOperations(JsonNode node) {
        List<GameOperation> operations = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return operations;
        }
        JsonNode array = node;
        if (node.isTextual()) {
            try {
                // 兼容部分模型把 operations 当成字符串输出的情况。
                array = objectMapper.readTree(node.asText());
            } catch (Exception e) {
                throw new GameBridgeException("operations 不是合法 JSON 数组: " + node.asText(), e);
            }
        }
        if (!array.isArray()) {
            throw new GameBridgeException("operations 必须是数组");
        }
        for (JsonNode item : array) {
            // toolName 是兼容字段，正常情况下模型应该使用 tool。
            String tool = item.path("tool").asText(item.path("toolName").asText(""));
            if (tool.isBlank()) {
                throw new GameBridgeException("operation 缺少 tool 字段: " + item);
            }
            JsonNode args = item.has("args") ? item.path("args") : objectMapper.createObjectNode();
            operations.add(new GameOperation(tool, args, item.path("note").asText("")));
        }
        return operations;
    }

    /**
     * 把结构化状态格式化为工具返回文本。
     *
     * @param requestedStatus 队列执行后的结构化状态
     * @param message         状态说明
     * @return 返回给 agent 的工具结果文本
     */
    private String formatStatus(GameBridgeActionStatus requestedStatus, String message) {
        GameBridgeActionStatus status = requestedStatus == null ? GameBridgeActionStatus.CONTINUE : requestedStatus;
        return switch (status) {
            case WAIT -> "[WAIT] " + message;
            case GAME_OVER -> "[GAME_OVER] " + message;
            default -> "[CONTINUE] " + message;
        };
    }
}
