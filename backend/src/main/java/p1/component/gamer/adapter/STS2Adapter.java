package p1.component.gamer.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.springframework.stereotype.Component;
import p1.config.mcp.MCPProperties;

/**
 * 杀戮尖塔 2 游戏适配器
 * <p>
 * 负责三件事：用 JSON 模式获取状态、修复出牌索引漂移、
 * 以及在 state_type 或手牌数量出现意外变化（比如出牌后手牌增加）时中断剩余队列。
 * <p>
 * 通过 {@code tool-prefix} 配置项（mcp-catalog.yaml）支持单人/多人模式切换。
 * 单人：tool-prefix 为空；多人：tool-prefix: "mp_"。
 */
@Component
public class STS2Adapter implements GameAdapter {

    private static final String TOOL_COMBAT_PLAY_CARD = "combat_play_card";
    private static final String LEGACY_TOOL_PLAY_CARD = "play_card";
    private static final String MP_PREFIX = "mp_";
    private static final String META_PLANNED_CARD_NAME = "plannedCardName";

    private final ObjectMapper objectMapper = new ObjectMapper();

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
     * 执行出牌前修复 card_index：如果当前索引指向的不是计划中的牌名，
     * 则在当前手牌中寻找同名卡并替换索引。
     */
    @Override
    public ToolExecutionRequest repairBeforeExecute(QueuedGameOperation operation, GameStateSnapshot currentState) {
        if (!isPlayCard(operation.request().name())) {
            return operation.request();
        }
        String plannedCardName = String.valueOf(operation.metadata().getOrDefault(META_PLANNED_CARD_NAME, ""));
        if (plannedCardName.isBlank()) {
            return operation.request();
        }

        JsonNode args = parseArgs(operation.request().arguments());
        Integer requestedIndex = readCardIndex(args);
        if (requestedIndex == null) {
            return operation.request();
        }

        JsonNode currentHand = hand(currentState);
        JsonNode currentAtIndex = currentHand.path(requestedIndex);
        if (!currentAtIndex.isMissingNode() && plannedCardName.equals(currentAtIndex.path("name").asText(""))) {
            return operation.request();
        }

        int repairedIndex = findCardByName(currentHand, plannedCardName);
        if (repairedIndex < 0) {
            throw new GameBridgeException("STS2 出牌索引已失效：计划打出「" + plannedCardName + "」，但当前手牌中没有同名牌。");
        }

        ObjectNode repairedArgs = args.deepCopy();
        writeCardIndex(repairedArgs, repairedIndex);
        return operation.request().toBuilder()
                .arguments(repairedArgs.toString())
                .build();
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
        String prefix = config.getToolPrefix();
        boolean hasPrefix = prefix != null && !prefix.isEmpty();
        StringBuilder sb = new StringBuilder();
        tools.tools().keySet().stream()
                .filter(spec -> !isStateTool(spec.name(), config))
                .filter(spec -> !hasPrefix || spec.name().startsWith(prefix))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .forEach(spec -> sb.append("- ")
                        .append(spec.name())
                        .append(": ")
                        .append(spec.description() == null ? "" : spec.description())
                        .append("\n"));
        return sb.isEmpty() ? "(没有可用操作工具)" : sb.toString();
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
