package p1.component.agent.gamer.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p1.config.mcp.MCPProperties;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 杀戮尖塔 2 游戏适配器
 * <p>
 * 负责三件事：用 JSON 模式获取状态、修复出牌索引漂移、
 * 以及在 state_type 或手牌数量出现意外变化（比如出牌后手牌增加）时中断剩余队列。
 * <p>
 * 首次获取状态时自动检测单人/多人模式（依次尝试 get_game_state 和 mp_get_game_state），
 * 无需手动在 mcp-catalog.yaml 中配置 tool-prefix 或 state-tool-name。
 */
@Component
@Slf4j
public class STS2Adapter implements GameAdapter {

    private static final String TOOL_COMBAT_PLAY_CARD = "combat_play_card";
    private static final String LEGACY_TOOL_PLAY_CARD = "play_card";
    private static final String MP_PREFIX = "mp_";
    private static final String META_PLANNED_CARD_NAME = "plannedCardName";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> detectedMode = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "sts2";
    }

    /**
     * STS2 的 get_game_state 默认返回 markdown，因此显式要求 JSON。
     */
    @Override
    public String stateToolArguments(GameAdapterContext context) {
        return "{\"format\":\"json\"}";
    }

    /**
     * 自动检测单人/多人模式并获取游戏状态。
     * <p>
     * 首次调用时依次尝试 get_game_state（单人）和 mp_get_game_state（多人），
     * 以第一个返回有效状态（含 state_type 且非 error）的工具为准，缓存检测结果。
     * 后续调用优先使用缓存模式；缓存失效时自动重新检测。
     */
    @Override
    public GameStateSnapshot fetchState(GameAdapterContext context) {
        String gameName = context.gameName();
        String cachedPrefix = detectedMode.get(gameName);

        if (cachedPrefix != null) {
            String toolName = cachedPrefix + context.config().getStateToolName();
            GameStateSnapshot state = tryFetchState(context, toolName);
            if (isValidState(state)) {
                return state;
            }
            log.info("[STS2] 缓存模式 prefix='{}' 已失效，重新检测: game={}", cachedPrefix, gameName);
            detectedMode.remove(gameName);
        }

        // 自动检测：先试单人，再试多人
        String spName = context.config().getStateToolName();
        GameStateSnapshot spState = tryFetchState(context, spName);
        if (isValidState(spState)) {
            detectedMode.put(gameName, "");
            log.info("[STS2] 检测到单人模式: game={}", gameName);
            return spState;
        }

        String mpName = MP_PREFIX + context.config().getStateToolName();
        GameStateSnapshot mpState = tryFetchState(context, mpName);
        if (isValidState(mpState)) {
            detectedMode.put(gameName, MP_PREFIX);
            log.info("[STS2] 检测到多人模式: game={}", gameName);
            return mpState;
        }

        if (spState != null) {
            return spState;
        }
        throw new GameBridgeException("无法检测游戏模式（单人/多人），get_game_state 和 mp_get_game_state 均不可用");
    }

    @Override
    public boolean isStateTool(String toolName, MCPProperties.GameMCPConfig config) {
        String baseName = config.getStateToolName();
        return toolName != null && (toolName.equals(baseName) || toolName.equals(MP_PREFIX + baseName));
    }

    /**
     * 在出牌操作入队时记录计划打出的牌名，供后续修复使用。
     */
    @Override
    public QueuedGameOperation prepareOperation(GameOperation operation, GameStateSnapshot plannedState) {
        QueuedGameOperation queued = QueuedGameOperation.from(operation, plannedState);
        if (!isPlayCard(operation.toolName())) {
            return queued;
        }

        Integer cardIndex = readCardIndex(operation.args());
        if (cardIndex == null) {
            return queued;
        }
        JsonNode card = hand(plannedState).path(cardIndex);
        if (!card.isMissingNode()) {
            queued.metadata().put(META_PLANNED_CARD_NAME, card.path("name").asText(""));
        }
        return queued;
    }

    /**
     * 批量入队时模拟手牌变化，确保每个操作的 plannedCardName 都基于前序操作
     * 执行后的预期手牌（而非静态快照的同一位置）。
     */
    @Override
    public ArrayDeque<QueuedGameOperation> prepareBatch(List<GameOperation> operations, GameStateSnapshot plannedState) {
        ArrayDeque<QueuedGameOperation> queue = new ArrayDeque<>();
        List<JsonNode> mutableHand = mutableHandCopy(plannedState);

        for (GameOperation op : operations) {
            QueuedGameOperation queued = QueuedGameOperation.from(op, plannedState);
            if (isPlayCard(op.toolName())) {
                Integer cardIndex = readCardIndex(op.args());
                if (cardIndex != null && cardIndex >= 0 && cardIndex < mutableHand.size()) {
                    JsonNode card = mutableHand.get(cardIndex);
                    if (!card.isMissingNode()) {
                        queued.metadata().put(META_PLANNED_CARD_NAME, card.path("name").asText(""));
                    }
                    mutableHand.remove(cardIndex.intValue());
                }
            }
            queue.offerLast(queued);
        }
        return queue;
    }

    /**
     * 执行出牌前修复 card_index：始终在当前手牌中按牌名查找，
     * 名字匹配优先于索引匹配；找不到同名卡时以当前手牌范围内索引作为兜底。
     */
    @Override
    public ToolExecutionRequest repairBeforeExecute(QueuedGameOperation operation, GameStateSnapshot currentState) {
        if (!isPlayCard(operation.request().name())) {
            return operation.request();
        }
        String plannedCardName = String.valueOf(operation.metadata().getOrDefault(META_PLANNED_CARD_NAME, ""));

        JsonNode args = parseArgs(operation.request().arguments());
        Integer requestedIndex = readCardIndex(args);
        if (requestedIndex == null) {
            return operation.request();
        }

        JsonNode currentHand = hand(currentState);
        int handSize = currentHand.isArray() ? currentHand.size() : 0;

        // 优先按牌名查找，无视索引变化。
        if (!plannedCardName.isBlank()) {
            int foundByName = findCardByName(currentHand, plannedCardName);
            if (foundByName >= 0) {
                if (foundByName == requestedIndex) {
                    return operation.request();
                }
                ObjectNode repairedArgs = args.deepCopy();
                writeCardIndex(repairedArgs, foundByName);
                return operation.request().toBuilder()
                        .arguments(repairedArgs.toString())
                        .build();
            }
        }

        // 兜底：名字找不到，但原始索引仍在当前手牌范围内，保持原样。
        if (requestedIndex >= 0 && requestedIndex < handSize) {
            return operation.request();
        }

        throw new GameBridgeException("STS2 出牌索引已失效：计划打出「"
                + (plannedCardName.isBlank() ? "(未知)" : plannedCardName)
                + "」，当前手牌共 " + handSize + " 张，请求索引=" + requestedIndex + "。");
    }

    /**
     * 执行后监视：state_type 变化或手牌数量非预期变化时中断队列。
     */
    @Override
    public void monitorAfterExecute(QueuedGameOperation operation,
                                    GameStateSnapshot beforeState,
                                    GameStateSnapshot afterState,
                                    String toolResult,
                                    boolean hasRemainingOperations) {
        String mcpError = parseMcpError(toolResult);
        if (mcpError != null) {
            throw new GameBridgeException("MCP 工具执行失败: " + mcpError);
        }

        if (!hasRemainingOperations) {
            return;
        }

        if (!safeEquals(beforeState.stateType(), afterState.stateType())) {
            throw new GameBridgeException("STS2 state_type 从 " + beforeState.stateType() + " 变为 " + afterState.stateType());
        }

        if (isPlayCard(operation.request().name())) {
            int beforeHand = hand(beforeState).size();
            int afterHand = hand(afterState).size();
            if (afterHand != beforeHand - 1) {
                String s = "STS2 出牌后手牌数量变化不符合预期：执行前 " + beforeHand + "，" +
                        "执行后 " + afterHand + "，" +
                        "剩余队列基于旧手牌不可靠；" +
                        "出现问题时执行的操作：" + operation.request().name() + "|" +
                        "agent操作描述：" + operation.note();
                throw new GameBridgeException(s);
            }
        }
    }

    // ── SP/MP 前缀处理 ──

    @Override
    public String resolveStateToolName(MCPProperties.GameMCPConfig config) {
        String prefix = config.getToolPrefix();
        return (prefix == null || prefix.isEmpty() ? "" : prefix) + config.getStateToolName();
    }

    @Override
    public String renderAvailableOperations(ToolProviderResult tools, MCPProperties.GameMCPConfig config) {
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

    // ── 自动检测 ──

    private GameStateSnapshot tryFetchState(GameAdapterContext context, String toolName) {
        ToolExecutor executor = context.tools().toolExecutorByName(toolName);
        if (executor == null) {
            return null;
        }
        try {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(stateToolArguments(context))
                    .build();
            String raw = executor.execute(request, context.sessionId());
            return parseState(raw);
        } catch (Exception e) {
            log.debug("[STS2] 状态工具 {} 尝试失败: {}", toolName, e.getMessage());
            return null;
        }
    }

    private boolean isValidState(GameStateSnapshot state) {
        if (state == null || state.json() == null) {
            return false;
        }
        if ("error".equals(state.json().path("status").asText(""))) {
            return false;
        }
        return !state.json().path("state_type").asText("").isBlank();
    }

    // ── 内部工具 ──

    private boolean isPlayCard(String toolName) {
        return TOOL_COMBAT_PLAY_CARD.equals(toolName)
                || LEGACY_TOOL_PLAY_CARD.equals(toolName)
                || (MP_PREFIX + TOOL_COMBAT_PLAY_CARD).equals(toolName)
                || (MP_PREFIX + LEGACY_TOOL_PLAY_CARD).equals(toolName);
    }

    private JsonNode hand(GameStateSnapshot state) {
        return state.json().path("player").path("hand");
    }

    private List<JsonNode> mutableHandCopy(GameStateSnapshot state) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode hand = hand(state);
        if (hand.isArray()) {
            hand.forEach(result::add);
        }
        return result;
    }

    private JsonNode parseArgs(String rawArgs) {
        try {
            return objectMapper.readTree(rawArgs == null || rawArgs.isBlank() ? "{}" : rawArgs);
        } catch (Exception e) {
            throw new GameBridgeException("解析工具参数失败: " + rawArgs, e);
        }
    }

    private Integer readCardIndex(JsonNode args) {
        if (args == null || args.isMissingNode() || args.isNull()) {
            return null;
        }
        if (args.has("card_index")) return args.path("card_index").asInt();
        if (args.has("cardIndex")) return args.path("cardIndex").asInt();
        if (args.has("index")) return args.path("index").asInt();
        return null;
    }

    private void writeCardIndex(ObjectNode args, int index) {
        if (args.has("cardIndex")) {
            args.put("cardIndex", index);
        } else if (args.has("index")) {
            args.put("index", index);
        } else {
            args.put("card_index", index);
        }
    }

    private int findCardByName(JsonNode hand, String name) {
        if (!hand.isArray()) {
            return -1;
        }
        for (int i = 0; i < hand.size(); i++) {
            if (name.equals(hand.path(i).path("name").asText(""))) {
                return i;
            }
        }
        return -1;
    }

    private boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private String parseMcpError(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(toolResult);
            if ("error".equals(root.path("status").asText(""))) {
                return root.path("error").asText("未知错误");
            }
        } catch (Exception ignored) {
            // 非 JSON 返回值，不是错误格式
        }
        return null;
    }
}
