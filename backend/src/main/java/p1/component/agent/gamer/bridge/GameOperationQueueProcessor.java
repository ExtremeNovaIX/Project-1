package p1.component.agent.gamer.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import p1.component.agent.gamer.adapter.*;
import p1.component.agent.gamer.memory.GamerWorkingMemoryService;
import p1.component.agent.gamer.trace.GamerDecisionTraceService;
import p1.component.agent.reasoning.ReasoningContentRecorder;
import p1.config.mcp.MCPProperties;
import reactor.util.function.Tuple5;

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
    private static final int FALLBACK_SUMMARY_OPERATION_LIMIT = 4;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, GameStateSnapshot> planningStates = new ConcurrentHashMap<>();
    private final Map<String, String> notices = new ConcurrentHashMap<>();
    private final Map<String, GameBridgeActionStatus> lastStatuses = new ConcurrentHashMap<>();
    private final Map<String, String> lastActionResults = new ConcurrentHashMap<>();
    private final Map<String, String> lastMessages = new ConcurrentHashMap<>();

    private final GamerWorkingMemoryService workingMemoryService;
    private final GamerDecisionTraceService traceService;
    private final ReasoningContentRecorder reasoningContentRecorder;

    /**
     * 创建游戏操作队列处理器。
     *
     * @param workingMemoryService gamer 纯内存工作记忆服务
     */
    public GameOperationQueueProcessor(GamerWorkingMemoryService workingMemoryService) {
        this(workingMemoryService, null, null);
    }

    @Autowired
    public GameOperationQueueProcessor(GamerWorkingMemoryService workingMemoryService,
                                       GamerDecisionTraceService traceService,
                                       ReasoningContentRecorder reasoningContentRecorder) {
        this.workingMemoryService = workingMemoryService;
        this.traceService = traceService;
        this.reasoningContentRecorder = reasoningContentRecorder;
    }

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
        lastMessages.remove(memoryId);
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
     * 查询上一批操作造成的结果和状态差异。
     *
     * @param memoryId LangChain4j 的会话记忆 id
     * @return 面向 agent 的上一批操作结果；没有结果时返回 null
     */
    public String peekLastActionResult(String memoryId) {
        return lastActionResults.get(memoryId);
    }

    /**
     * 查询最近一次 enqueue_operations 中模型生成的用户可见消息。
     *
     * @param memoryId LangChain4j 的会话记忆 id
     * @return 用户可见消息；没有时返回空字符串
     */
    public String lastMessage(String memoryId) {
        return lastMessages.getOrDefault(memoryId, "");
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
        GameBridgeActionStatus requestedStatus = GameBridgeActionStatus.CONTINUE;
        String summary = "";
        String reasoningContent = consumeReasoningContent(key);
        List<GameOperation> operations = List.of();
        GameStateSnapshot plannedState = planningStates.get(key);
        boolean expectMoreOperations = false;
        try {
            // 解析虚拟工具参数，后续所有执行都基于这个批次的 operations。
            JsonNode root = objectMapper.readTree(rawArguments == null || rawArguments.isBlank() ? "{}" : rawArguments);
            requestedStatus = GameBridgeActionStatus.from(root.path("status").asText("CONTINUE"));
            expectMoreOperations = root.path("_expect_more_operations").asBoolean(false);
            operations = parseOperations(root.path("operations"));
            String userMessage = root.path("message").asText(null);
            if (userMessage != null && !userMessage.isBlank()) {
                lastMessages.put(key, normalizeText(userMessage));
            }
            summary = normalizeSummary(root, requestedStatus, operations);
            if (operations.isEmpty()) {
                String result = "未提交操作。" + summary;
                lastStatuses.put(key, requestedStatus);
                rememberLastActionAndTrace(gameName, key, requestedStatus, summary, reasoningContent, operations, result, "无状态变化。", null);
                recordMemorySafely(gameName, key, requestedStatus, summary, reasoningContent, operations, result, null);
                return formatStatus(requestedStatus, result);
            }
            log.info("[游戏桥接] 收到操作队列: game={}, memoryId={}, operations={}", gameName, key, operations);

            // plannedState 是 agent 做计划时看到的状态，用于记录“原本想操作哪张牌”等语义信息。
            if (plannedState == null) {
                plannedState = adapter.fetchState(context);
            }
            // 队列只在本次 enqueue_operations 调用内存在；adapter 只做确定性修复准备，不做能量等推测裁剪。
            ArrayDeque<QueuedGameOperation> queue = adapter.prepareBatch(operations, plannedState);
            if (queue.isEmpty()) {
                String result = "操作队列为空。 " + summary;
                lastStatuses.put(key, requestedStatus);
                rememberLastActionAndTrace(gameName, key, requestedStatus, summary, reasoningContent, operations, result, "无状态变化。", null);
                recordMemorySafely(gameName, key, requestedStatus, summary, reasoningContent, operations, result, null);
                return formatStatus(requestedStatus, result);
            }

            // drainQueue 内部仍然逐条调用 MCP；单条业务失败可被 adapter 按最新状态软化并继续。
            DrainResult drainResult = drainQueue(key, adapter, context, queue, plannedState, expectMoreOperations);
            planningStates.put(key, drainResult.latestState());
            lastStatuses.put(key, requestedStatus);
            if (!drainResult.softFailures().isEmpty()) {
                notices.put(key, renderSoftFailureNotice(drainResult.softFailures()));
            }
            String result = renderDrainResult(summary, operations.size(), drainResult);
            String stateDiff = adapter.renderStateDiffForAgent(plannedState, drainResult.latestState());
            rememberLastActionAndTrace(gameName, key, requestedStatus, summary, reasoningContent, operations, result, stateDiff, null);
            recordMemorySafely(gameName, key, requestedStatus, summary, reasoningContent, operations, result, null);
            return formatStatus(requestedStatus, result);
        } catch (GameBridgeException e) {
            log.warn("[游戏操作队列中断] game={}, memoryId={}, executed={}, reason={}", gameName, key, e.getExecutedCount(), e.getMessage());
            String cleanReason = compactInterruptReason(e.getMessage());
            lastStatuses.put(key, GameBridgeActionStatus.INTERRUPTED);
            notices.put(key, cleanReason);
            String result = "队列中断，已执行 " + e.getExecutedCount() + " 条操作，剩余操作已丢弃。";
            rememberLastActionAndTrace(
                    gameName,
                    key,
                    GameBridgeActionStatus.INTERRUPTED,
                    summary,
                    reasoningContent,
                    operations,
                    result,
                    "队列中断，状态 diff 未完成。",
                    cleanReason);
            recordMemorySafely(
                    gameName,
                    key,
                    GameBridgeActionStatus.INTERRUPTED,
                    summary,
                    reasoningContent,
                    operations,
                    result,
                    cleanReason);
            return "[CONTINUE] 操作队列已中断，剩余指令已丢弃。" + cleanReason + "\n请基于下一次注入的最新状态重新决策。";
        } catch (Exception e) {
            log.error("[游戏桥接] 操作队列处理失败: game={}, memoryId={}", gameName, key, e);
            lastStatuses.put(key, GameBridgeActionStatus.CONTINUE);
            notices.put(key, "桥接层处理失败: " + e.getMessage());
            String result = "桥接层处理失败，队列已丢弃。";
            rememberLastActionAndTrace(
                    gameName,
                    key,
                    GameBridgeActionStatus.CONTINUE,
                    summary,
                    reasoningContent,
                    operations,
                    result,
                    "桥接层处理失败，状态 diff 未完成。",
                    e.getMessage());
            recordMemorySafely(
                    gameName,
                    key,
                    GameBridgeActionStatus.CONTINUE,
                    summary,
                    reasoningContent,
                    operations,
                    result,
                    e.getMessage());
            return "[CONTINUE] 桥接层处理操作队列失败，已丢弃队列。原因: " + e.getMessage() + "\n请基于下一次注入的最新状态重新决策。";
        }
    }

    /**
     * 保存上一批操作结果，并追加复盘日志。
     *
     * @param gameName        游戏名
     * @param key             会话 key
     * @param status          队列状态
     * @param summary         agent 提交的决策摘要
     * @param reasoning       模型返回的 reasoning_content；没有时为空
     * @param operations      agent 提交的操作队列
     * @param result          桥接层执行结果
     * @param stateDiff       操作造成的状态差异
     * @param interruptReason 中断原因；没有中断时为空
     */
    private void rememberLastActionAndTrace(String gameName,
                                            String key,
                                            GameBridgeActionStatus status,
                                            String summary,
                                            String reasoning,
                                            List<GameOperation> operations,
                                            String result,
                                            String stateDiff,
                                            String interruptReason) {
        String rendered = renderLastActionResult(status, summary, reasoning, operations, result, stateDiff, interruptReason);
        lastActionResults.put(key, rendered);
        if (traceService != null) {
            traceService.appendQueueTrace(gameName, key, status, summary, reasoning, operations, result, stateDiff, interruptReason);
        }
    }

    /**
     * 渲染下一轮注入给 agent 的上一批操作结果。
     */
    private String renderLastActionResult(GameBridgeActionStatus status,
                                          String summary,
                                          String reasoning,
                                          List<GameOperation> operations,
                                          String result,
                                          String stateDiff,
                                          String interruptReason) {
        StringBuilder sb = new StringBuilder();
        sb.append("status=").append(status == null ? GameBridgeActionStatus.UNKNOWN : status).append("\n");
        sb.append("decision=").append(summary == null || summary.isBlank() ? "无" : summary.trim()).append("\n");
        sb.append("operations:\n");
        if (operations == null || operations.isEmpty()) {
            sb.append("- 无\n");
        } else {
            for (GameOperation operation : operations) {
                sb.append("- ")
                        .append(operation.toolName())
                        .append(" args=")
                        .append(operation.args() == null ? "{}" : operation.args())
                        .append(" | 意图=")
                        .append(operation.note() == null || operation.note().isBlank() ? "无" : operation.note().trim())
                        .append("\n");
            }
        }
        sb.append("result=").append(result == null || result.isBlank() ? "无" : result.trim()).append("\n");
        if (interruptReason != null && !interruptReason.isBlank()) {
            sb.append("interrupt=").append(interruptReason.trim()).append("\n");
        }
        sb.append("state_diff:\n").append(stateDiff == null || stateDiff.isBlank() ? "无状态差异。" : stateDiff.trim());
        return sb.toString();
    }

    /**
     * 安全记录 gamer 工作记忆，避免记忆压缩失败影响真实游戏操作结果。
     *
     * @param gameName        游戏名
     * @param key             会话 key
     * @param status          本次队列状态
     * @param summary         agent 提交的决策摘要
     * @param reasoning       模型返回的 reasoning_content；没有时为空
     * @param operations      agent 提交的操作队列
     * @param result          桥接层执行结果
     * @param interruptReason 队列中断原因；没有中断时为空
     */
    private void recordMemorySafely(String gameName,
                                    String key,
                                    GameBridgeActionStatus status,
                                    String summary,
                                    String reasoning,
                                    List<GameOperation> operations,
                                    String result,
                                    String interruptReason) {
        try {
            // 工作记忆只保留决策结论和执行反馈；完整 reasoning 只进入复盘日志，避免下轮 prompt 膨胀。
            workingMemoryService.recordQueueResult(gameName, key, status, summary, "", operations, result, interruptReason);
        } catch (Exception e) {
            log.warn("[游戏桥接] gamer 工作记忆记录失败: game={}, memoryId={}, reason={}", gameName, key, e.getMessage());
        }
    }

    /**
     * 取出本次模型回复对应的 reasoning_content。
     *
     * @param key 会话 key
     * @return 最近一次 reasoning_content；没有时为空字符串
     */
    private String consumeReasoningContent(String key) {
        if (reasoningContentRecorder == null) {
            return "";
        }
        return reasoningContentRecorder.consumeLatest(key);
    }

    /**
     * 压缩中断原因，去掉异常消息中附带的完整状态文本。
     * <p>
     * 最新状态会在下一轮 latest_game_state 中重新注入，notice 和工作记忆只需要保存中断原因。
     *
     * @param message 原始异常消息
     * @return 单行中断摘要
     */
    private String compactInterruptReason(String message) {
        String normalized = normalizeText(message);
        int stateIndex = normalized.indexOf("最新状态:");
        if (stateIndex >= 0) {
            normalized = normalized.substring(0, stateIndex).trim();
        }
        return normalized;
    }

    /**
     * 执行队列中的所有操作。
     *
     * @param key          会话 key
     * @param adapter      当前游戏适配器
     * @param context      适配器运行上下文
     * @param queue        待执行操作队列
     * @param initialState agent 规划时的初始状态
     * @param expectMoreOperations true 表示当前批次结束后同一 stream 仍可能继续输出后续操作
     * @return 队列执行结果，包含最新状态、成功数和软错误列表
     * @throws GameBridgeException 修复失败、工具不存在、执行异常或监视中断时抛出
     */
    private DrainResult drainQueue(String key,
                                   GameAdapter adapter,
                                   GameAdapterContext context,
                                   ArrayDeque<QueuedGameOperation> queue,
                                   GameStateSnapshot initialState,
                                   boolean expectMoreOperations) {
        int attempted = 0;
        int successful = 0;
        List<SoftOperationFailure> softFailures = new ArrayList<>();
        GameStateSnapshot currentState = initialState;
        while (!queue.isEmpty()) {
            QueuedGameOperation operation = queue.pollFirst();
            GameStateSnapshot beforeState = currentState != null ? currentState : adapter.fetchState(context);
            ToolExecutionRequest request;
            try {
                request = adapter.repairBeforeExecute(operation, beforeState);
            } catch (GameBridgeException e) {
                attempted++;
                OperationFailureHandling handling = handleOperationFailure(
                        key, adapter, context, operation, beforeState,
                        "adapter执行指令修复失败: " + e.getMessage(),
                        successful, softFailures);
                currentState = handling.latestState();
                continue;
            }

            ToolExecutor executor = context.tools().toolExecutorByName(request.name());
            if (executor == null) {
                throw new GameBridgeException("MCP 工具不存在: " + request.name(), successful);
            }

            String toolResult;
            try {
                toolResult = executor.execute(request, key);
            } catch (Exception e) {
                throw new GameBridgeException("MCP 工具执行失败: " + request.name() + "，原因: " + e.getMessage(), successful);
            }

            attempted++;

            QueuedGameOperation executedOperation = operation.withRequest(request);
            String mcpError = adapter.extractToolError(toolResult);
            if (mcpError != null) {
                OperationFailureHandling handling = handleOperationFailure(
                        key, adapter, context, executedOperation, beforeState,
                        "MCP 工具执行失败: " + mcpError,
                        successful, softFailures);
                currentState = handling.latestState();
                continue;
            }
            if (isTextToolError(toolResult)) {
                throw new GameBridgeException("MCP 工具执行失败: " + toolResult, successful);
            }

            GameStateSnapshot afterState = fetchStateUntilMonitorPasses(
                    key, adapter, context, executedOperation, beforeState, toolResult,
                    !queue.isEmpty() || expectMoreOperations,
                    successful + 1);
            currentState = afterState;
            successful++;
        }
        return new DrainResult(currentState, attempted, successful, List.copyOf(softFailures));
    }

    /**
     * 处理单条操作失败：获取最新状态，并交给 adapter 判断是否可以作为软错误继续。
     *
     * @param key          会话 key
     * @param adapter      当前游戏适配器
     * @param context      适配器运行上下文
     * @param operation    失败操作
     * @param beforeState  失败前状态
     * @param reason       失败原因
     * @param successful   当前批次已成功执行的 MCP 操作数
     * @param softFailures 当前批次累计的软错误列表
     * @return 失败处理结果；如果不能软化会直接抛出硬中断异常
     */
    private OperationFailureHandling handleOperationFailure(String key,
                                                           GameAdapter adapter,
                                                           GameAdapterContext context,
                                                           QueuedGameOperation operation,
                                                           GameStateSnapshot beforeState,
                                                           String reason,
                                                           int successful,
                                                           List<SoftOperationFailure> softFailures) {
        GameStateSnapshot latestState;
        try {
            latestState = adapter.fetchState(context);
        } catch (Exception e) {
            throw new GameBridgeException(reason + "；失败后获取最新状态也失败: " + e.getMessage(), successful);
        }

        if (adapter.shouldContinueAfterOperationFailure(operation, beforeState, latestState, reason)) {
            SoftOperationFailure failure = new SoftOperationFailure(
                    operation.request().name(),
                    operation.request().arguments(),
                    operation.note(),
                    reason);
            softFailures.add(failure);
            log.info("[游戏桥接] 操作失败但状态仍可继续，已跳过该操作: memoryId={}, tool={}, reason={}",
                    key, failure.toolName(), reason);
            return new OperationFailureHandling(latestState);
        }

        throw new GameBridgeException(reason + "\n最新状态:\n" + adapter.renderStateForAgent(latestState), successful);
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
     * 渲染本次队列执行结果，包含软错误统计。
     */
    private String renderDrainResult(String summary, int requestedOperations, DrainResult drainResult) {
        int failed = drainResult.softFailures().size();
        String prefix = failed == 0
                ? "已成功执行 " + drainResult.successful() + "/" + requestedOperations + " 条操作。"
                : "已成功执行 " + drainResult.successful() + "/" + requestedOperations
                + " 条操作，跳过 " + failed + " 条软错误操作。";
        return prefix + " " + summary;
    }

    /**
     * 渲染下一轮注入给 agent 的软错误摘要。
     */
    private String renderSoftFailureNotice(List<SoftOperationFailure> failures) {
        StringBuilder sb = new StringBuilder("上一批队列中有 ")
                .append(failures.size())
                .append(" 条操作失败但游戏仍处于可行动窗口，已跳过失败操作并继续执行其余队列：\n");
        for (SoftOperationFailure failure : failures) {
            sb.append("- ")
                    .append(failure.toolName())
                    .append(" args=")
                    .append(failure.arguments())
                    .append(" | 意图=")
                    .append(failure.note())
                    .append(" | 原因=")
                    .append(failure.reason())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 识别 Python MCP 包装层返回的文本错误。
     * <p>
     * 这类错误通常来自连接失败或 HTTP 异常，不能按游戏内单条业务错误软化。
     */
    private boolean isTextToolError(String toolResult) {
        return toolResult != null && toolResult.stripLeading().startsWith("Error:");
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
            operations.add(new GameOperation(tool, args, normalizeText(item.path("note").asText(""))));
        }
        return operations;
    }

    /**
     * 解析并规范化本批操作摘要。
     * <p>
     * 模型偶尔会漏填 summary 或改用 decision_summary；桥接层在这里做兼容和兜底，
     * 避免为了非关键说明字段触发额外 LLM 往返。
     *
     * @param root       enqueue_operations 的根 JSON
     * @param status     本批队列状态
     * @param operations 已解析的操作队列
     * @return 规范化后的摘要
     */
    private String normalizeSummary(JsonNode root, GameBridgeActionStatus status, List<GameOperation> operations) {
        String explicitSummary = firstNonBlank(
                text(root.path("summary")),
                text(root.path("decision_summary")));
        if (!explicitSummary.isBlank()) {
            return normalizeText(explicitSummary);
        }

        String fallback = buildFallbackSummary(status, operations);
        log.warn("[游戏桥接] enqueue_operations 缺少 summary，已自动生成摘要: {}", fallback);
        return fallback;
    }

    /**
     * 根据操作队列生成摘要兜底文本。
     *
     * @param status     本批队列状态
     * @param operations 已解析的操作队列
     * @return 可写入记忆和复盘日志的简短摘要
     */
    private String buildFallbackSummary(GameBridgeActionStatus status, List<GameOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return status == GameBridgeActionStatus.WAIT
                    ? "当前无可执行操作，等待下一次状态刷新。"
                    : "未提交具体操作，等待桥接层返回最新状态。";
        }

        StringBuilder sb = new StringBuilder("执行：");
        int count = 0;
        for (GameOperation operation : operations) {
            if (count > 0) {
                sb.append("；");
            }
            sb.append(operationBrief(operation));
            count++;
            if (count >= FALLBACK_SUMMARY_OPERATION_LIMIT) {
                break;
            }
        }
        if (operations.size() > FALLBACK_SUMMARY_OPERATION_LIMIT) {
            sb.append("；等").append(operations.size()).append("步");
        }
        return sb.toString();
    }

    /**
     * 渲染单条操作的兜底摘要片段。
     *
     * @param operation 操作对象
     * @return 优先使用 note；没有 note 时退化为工具名
     */
    private String operationBrief(GameOperation operation) {
        if (operation == null) {
            return "未知操作";
        }
        String note = normalizeText(operation.note());
        if (!note.isBlank()) {
            return note;
        }
        return operation.toolName() == null ? "未知操作" : operation.toolName();
    }

    /**
     * 提取 JSON 节点文本。
     *
     * @param node JSON 节点
     * @return 缺失时返回空字符串
     */
    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    /**
     * 返回第一段非空文本。
     *
     * @param values 候选文本
     * @return 非空文本；都为空时返回空字符串
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String normalized = normalizeText(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    /**
     * 合并多余空白，避免模型输出换行或 JSON 代码块污染摘要。
     *
     * @param value 原始文本
     * @return 单行文本
     */
    private String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    /**
     * 队列执行统计。
     */
    private record DrainResult(
            GameStateSnapshot latestState,
            int attempted,
            int successful,
            List<SoftOperationFailure> softFailures
    ) {
    }

    /**
     * 单条软错误操作摘要。
     */
    private record SoftOperationFailure(
            String toolName,
            String arguments,
            String note,
            String reason
    ) {
    }

    /**
     * 单条失败处理后的最新状态。
     */
    private record OperationFailureHandling(GameStateSnapshot latestState) {
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
