package p1.component.agent.gamer.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p1.config.mcp.MCPProperties;

import java.util.*;
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
    private static final List<String> VIRTUAL_CARD_NAME_FIELDS = List.of("card", "card_name", "cardName", "name");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> detectedMode = new ConcurrentHashMap<>();
    private volatile String lastFetchedGameName;
    private final StateDiffRenderer stateDiffRenderer = new StateDiffRenderer();
    private final OperationToolRenderer operationToolRenderer = new OperationToolRenderer();
    private final PlayCardPlanCompiler playCardPlanCompiler = new PlayCardPlanCompiler();

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
        lastFetchedGameName = gameName;
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
        throw new GameBridgeException("无法检测STS2游戏模式（单人/多人），get_game_state 和 mp_get_game_state 均不可用");
    }

    @Override
    public boolean isStateTool(String toolName, MCPProperties.GameMCPConfig config) {
        String baseName = config.getStateToolName();
        return toolName != null && (toolName.equals(baseName) || toolName.equals(MP_PREFIX + baseName));
    }

    /**
     * 判断 STS2 当前状态是否需要 agent 行动。
     * <p>
     * 战斗内只在玩家回合且行动阶段时行动；非战斗交互状态先交给 agent 判断具体操作。
     */
    @Override
    public GameActionability evaluateActionability(GameStateSnapshot state) {
        if (state == null || state.json() == null) {
            return GameActionability.unknown("STS2 状态为空，无法判断行动窗口");
        }

        JsonNode root = state.json();
        if ("error".equals(root.path("status").asText(""))) {
            return GameActionability.unknown("STS2 状态工具返回错误: " + root.path("error").asText("未知错误"));
        }

        String stateType = state.stateType();
        if (stateType.isBlank()) {
            return GameActionability.unknown("STS2 状态缺少 state_type");
        }

        if ("game_over".equals(stateType)) {
            return GameActionability.gameOver("STS2 state_type=" + state.stateType() + "，游戏已结束");
        }

        JsonNode battle = root.path("battle");
        if (battle.isObject() && !battle.isEmpty()) {
            String turn = battle.path("turn").asText("");
            boolean playPhase = battle.path("is_play_phase").asBoolean(false);
            if ("player".equals(turn) && playPhase) {
                return GameActionability.actionable("STS2 战斗中处于玩家行动阶段");
            }
            return GameActionability.waiting("STS2 战斗中暂不可行动: turn="
                    + battle.path("turn").asText("(未知)")
                    + ", is_play_phase=" + playPhase);
        }

        if ("monster".equals(stateType)) {
            return GameActionability.unknown("STS2 state_type=monster 但缺少 battle 信息，暂不行动");
        }

        return GameActionability.actionable("STS2 非战斗交互状态可由 agent 决策: state_type=" + state.stateType());
    }

    /**
     * 将 STS2 MCP 返回的源状态直接注入给 agent。
     * <p>
     * 状态不再做 K-V 扁平化或字段清洗，避免界面特有字段、嵌套说明和 MCP 原生结构在渲染层丢失。
     */
    @Override
    public String renderStateForAgent(GameStateSnapshot state) {
        if (state == null || state.json() == null) {
            return "(未能获取 STS2 状态)";
        }

        String raw = state.rawJson();
        return raw == null || raw.isBlank() ? state.json().toString() : raw;
    }

    /**
     * 渲染 STS2 操作前后的白名单状态差异。
     */
    @Override
    public String renderStateDiffForAgent(GameStateSnapshot before, GameStateSnapshot after) {
        return stateDiffRenderer.renderDiff(before, after);
    }

    /**
     * 在出牌操作入队时记录计划打出的牌名，供后续修复使用。
     */
    @Override
    public QueuedGameOperation prepareOperation(GameOperation operation, GameStateSnapshot plannedState) {
        return playCardPlanCompiler.prepareOperation(operation, plannedState);
    }

    /**
     * 批量入队时模拟手牌变化，确保每个操作的 plannedCardName 都基于前序操作
     * 执行后的预期手牌（而非静态快照的同一位置）。
     * <p>
     * 这里不做能量、can_play 等合法性裁剪；MCP 是最终裁判。adapter 只记录计划牌名，
     * 便于执行前把牌名修复为当前真实手牌下标。单条失败由队列处理器按最新状态软化或中断。
     */
    @Override
    public ArrayDeque<QueuedGameOperation> prepareBatch(List<GameOperation> operations, GameStateSnapshot plannedState) {
        return playCardPlanCompiler.prepareBatch(operations, plannedState);
    }

    /**
     * 执行出牌前把 card 名称翻译成 card_index。
     * <p>
     * agent 面向牌名决策；真实 STS2 MCP 仍然需要 card_index，因此这里在当前手牌中按名字查找。
     * 旧格式 card_index 仍保留兼容，但名字匹配优先于索引匹配。
     */
    @Override
    public ToolExecutionRequest repairBeforeExecute(QueuedGameOperation operation, GameStateSnapshot currentState) {
        if (!isPlayCard(operation.request().name())) {
            return operation.request();
        }
        String plannedCardName = String.valueOf(operation.metadata().getOrDefault(META_PLANNED_CARD_NAME, ""));

        JsonNode args = parseArgs(operation.request().arguments());
        String requestedCardName = readCardName(args);
        if (!requestedCardName.isBlank()) {
            plannedCardName = requestedCardName;
        }

        JsonNode currentHand = hand(currentState);
        int handSize = currentHand.isArray() ? currentHand.size() : 0;

        if (!plannedCardName.isBlank()) {
            int foundByName = findCardByName(currentHand, plannedCardName);
            if (foundByName >= 0) {
                ObjectNode repairedArgs = args.deepCopy();
                removeVirtualCardNameFields(repairedArgs);
                writeCardIndex(repairedArgs, foundByName);
                if (isSelfTargetCard(currentHand.path(foundByName))) {
                    repairedArgs.remove("target");
                }
                return operation.request().toBuilder()
                        .arguments(repairedArgs.toString())
                        .build();
            }
        }

        Integer requestedIndex = readCardIndex(args);
        if (requestedIndex == null) {
            throw new GameBridgeException("STS2 出牌缺少 card 牌名：请使用 args.card 指定要打出的手牌名称。当前手牌: "
                    + renderHandNames(currentHand));
        }

        // 兜底：名字找不到，但原始索引仍在当前手牌范围内，保持原样。
        if (requestedIndex >= 0 && requestedIndex < handSize) {
            ObjectNode repairedArgs = args.deepCopy();
            if (isSelfTargetCard(currentHand.path(requestedIndex))) {
                repairedArgs.remove("target");
            }
            return operation.request().toBuilder()
                    .arguments(repairedArgs.toString())
                    .build();
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
        String mcpError = extractToolError(toolResult);
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
    public String renderAvailableOperations(ToolProviderResult tools,
                                            MCPProperties.GameMCPConfig config,
                                            GameStateSnapshot state) {
        return operationToolRenderer.render(tools, config, state);
    }

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
        if (state == null || state.json() == null) {
            return objectMapper.createArrayNode();
        }
        return state.json().path("player").path("hand");
    }

    /**
     * STS2 状态差异渲染器。
     * <p>
     * latest_game_state 已经直接注入 MCP 源 JSON；diff 仍保持短摘要，避免上一轮结果把 prompt 撑大。
     */
    private class StateDiffRenderer {

        /**
         * 渲染操作前后状态白名单 diff，避免大段描述文本污染下一轮决策。
         */
        private String renderDiff(GameStateSnapshot before, GameStateSnapshot after) {
            JsonNode beforeRoot = before == null ? objectMapper.createObjectNode() : before.json();
            JsonNode afterRoot = after == null ? objectMapper.createObjectNode() : after.json();
            List<String> changes = new ArrayList<>();
            compare(changes, "state.type", stateType(before), stateType(after));
            compare(changes, "run.act", text(beforeRoot.path("run").path("act")), text(afterRoot.path("run").path("act")));
            compare(changes, "run.floor", text(beforeRoot.path("run").path("floor")), text(afterRoot.path("run").path("floor")));
            compare(changes, "battle.round", text(beforeRoot.path("battle").path("round")), text(afterRoot.path("battle").path("round")));
            compare(changes, "battle.turn", text(beforeRoot.path("battle").path("turn")), text(afterRoot.path("battle").path("turn")));
            compare(changes, "battle.is_play_phase", text(beforeRoot.path("battle").path("is_play_phase")), text(afterRoot.path("battle").path("is_play_phase")));
            compare(changes, "player.hp", hp(beforeRoot.path("player")), hp(afterRoot.path("player")));
            compare(changes, "player.block", text(beforeRoot.path("player").path("block")), text(afterRoot.path("player").path("block")));
            compare(changes, "player.energy", energy(beforeRoot.path("player")), energy(afterRoot.path("player")));
            compare(changes, "player.gold", text(beforeRoot.path("player").path("gold")), text(afterRoot.path("player").path("gold")));
            compare(changes, "player.hand", cardNames(beforeRoot.path("player").path("hand")), cardNames(afterRoot.path("player").path("hand")));
            compare(changes, "piles", pileCounts(beforeRoot.path("player")), pileCounts(afterRoot.path("player")));
            compare(changes, "enemies", enemySummary(beforeRoot.path("battle").path("enemies")), enemySummary(afterRoot.path("battle").path("enemies")));
            compare(changes, "map.current", mapCurrent(beforeRoot.path("map")), mapCurrent(afterRoot.path("map")));
            compare(changes, "map.next_options", mapOptions(beforeRoot.path("map").path("next_options")), mapOptions(afterRoot.path("map").path("next_options")));
            compare(changes, "rewards", genericSummary(beforeRoot.path("rewards")), genericSummary(afterRoot.path("rewards")));
            compare(changes, "card_select", genericSummary(beforeRoot.path("card_select")), genericSummary(afterRoot.path("card_select")));
            if (changes.isEmpty()) {
                return "- 状态签名无变化。";
            }
            return String.join("\n", changes);
        }

        /**
         * 渲染地图节点。
         */
        private String renderMapNode(JsonNode node) {
            if (!node.isObject() || node.isEmpty()) {
                return "";
            }
            List<String> parts = new ArrayList<>();
            String coord = coordinate(node);
            if (!coord.isBlank()) {
                parts.add("node=" + coord);
            }
            addPart(parts, "type", text(node.path("type")));
            addPart(parts, "visited", text(node.path("visited")));
            addPart(parts, "children", children(node.path("children")));
            return String.join(" ", parts);
        }

        /**
         * 将对象中的标量字段渲染成单行。
         */
        private String inlineScalars(JsonNode object) {
            List<String> parts = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (isScalar(value) && shouldRenderScalar(value)) {
                    parts.add(field.getKey() + "=" + scalar(value));
                }
            }
            return parts.isEmpty() ? compact(object) : String.join(" ", parts);
        }

        /**
         * 追加 diff 行。
         */
        private void compare(List<String> changes, String key, String before, String after) {
            String b = firstNonBlank(before, "(空)");
            String a = firstNonBlank(after, "(空)");
            if (!b.equals(a)) {
                changes.add("- " + key + ": " + b + " -> " + a);
            }
        }

        /**
         * 添加非空字段。
         */
        private void addPart(List<String> parts, String key, String value) {
            if (value != null && !value.isBlank()) {
                parts.add(key + "=" + value);
            }
        }

        private String stateType(GameStateSnapshot state) {
            return state == null ? "" : firstNonBlank(state.stateType(), text(state.json().path("state_type")));
        }

        private String hp(JsonNode player) {
            String hp = text(player.path("hp"));
            String maxHp = text(player.path("max_hp"));
            if (hp.isBlank() && maxHp.isBlank()) {
                return "";
            }
            return hp + "/" + maxHp;
        }

        private String energy(JsonNode player) {
            String energy = text(player.path("energy"));
            String maxEnergy = text(player.path("max_energy"));
            if (energy.isBlank() && maxEnergy.isBlank()) {
                return "";
            }
            return energy + "/" + maxEnergy;
        }

        private String pileCounts(JsonNode player) {
            List<String> parts = new ArrayList<>();
            addPart(parts, "draw", text(player.path("draw_pile_count")));
            addPart(parts, "discard", text(player.path("discard_pile_count")));
            addPart(parts, "exhaust", text(player.path("exhaust_pile_count")));
            return String.join(" ", parts);
        }

        private String cardNames(JsonNode cards) {
            if (!cards.isArray() || cards.isEmpty()) {
                return "";
            }
            List<String> names = new ArrayList<>();
            for (JsonNode card : cards) {
                names.add(firstNonBlank(text(card.path("name")), scalar(card)));
            }
            return String.join(" | ", names);
        }

        private String enemySummary(JsonNode enemies) {
            if (!enemies.isArray() || enemies.isEmpty()) {
                return "";
            }
            List<String> rendered = new ArrayList<>();
            for (JsonNode enemy : enemies) {
                String id = firstNonBlank(text(enemy.path("entity_id")), text(enemy.path("name")), "?");
                rendered.add(id + ":" + text(enemy.path("hp")) + "/" + text(enemy.path("max_hp"))
                        + " block=" + text(enemy.path("block"))
                        + " intent=" + genericSummary(enemy.path("intents"))
                        + " status=" + genericSummary(enemy.path("status")));
            }
            return String.join(" | ", rendered);
        }

        private String mapCurrent(JsonNode map) {
            if (!map.isObject() || map.isEmpty()) {
                return "";
            }
            return firstNonBlank(renderMapNode(map.path("current_position")), renderMapNode(map.path("current_node")));
        }

        private String mapOptions(JsonNode options) {
            if (!options.isArray() || options.isEmpty()) {
                return "";
            }
            List<String> values = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                values.add(i + ":" + renderMapNode(options.get(i)));
            }
            return String.join(" | ", values);
        }

        private String genericSummary(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull() || node.isEmpty()) {
                return "";
            }
            if (isScalar(node)) {
                return scalar(node);
            }
            if (node.isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode item : node) {
                    values.add(item.isObject() ? inlineScalars(item) : scalar(item));
                }
                return String.join(" | ", values);
            }
            if (node.isObject()) {
                return inlineScalars(node);
            }
            return compact(node);
        }

        private String coordinate(JsonNode node) {
            String col = text(node.path("col"));
            String row = text(node.path("row"));
            if (col.isBlank() || row.isBlank()) {
                return "";
            }
            return "(" + col + "," + row + ")";
        }

        private String children(JsonNode children) {
            if (!children.isArray() || children.isEmpty()) {
                return "";
            }
            List<String> values = new ArrayList<>();
            for (JsonNode child : children) {
                if (child.isArray() && child.size() >= 2) {
                    values.add("(" + text(child.get(0)) + "," + text(child.get(1)) + ")");
                } else if (child.isObject()) {
                    values.add(coordinate(child));
                } else {
                    values.add(scalar(child));
                }
            }
            values.removeIf(String::isBlank);
            return String.join(",", values);
        }

        private boolean isScalar(JsonNode node) {
            return node == null
                    || node.isMissingNode()
                    || node.isNull()
                    || node.isValueNode();
        }

        private boolean shouldRenderScalar(JsonNode node) {
            return node != null
                    && !node.isMissingNode()
                    && !node.isNull()
                    && (!node.isTextual() || !node.asText("").isBlank());
        }

        private String scalar(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return "";
            }
            if (node.isTextual()) {
                return node.asText("");
            }
            return node.asText(node.toString());
        }

        private String text(JsonNode node) {
            return scalar(node);
        }

        private String compact(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return "";
            }
            return node.toString();
        }

        private String firstNonBlank(String... values) {
            if (values == null) {
                return "";
            }
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
            return "";
        }
    }

    /**
     * STS2 工具列表渲染器。
     * <p>
     * 该内部类根据当前 state_type 收窄暴露给 agent 的操作工具，减少工具选择噪音。
     */
    private class OperationToolRenderer {
        /**
         * 渲染当前状态下可用的 MCP 操作工具。
         */
        private String render(ToolProviderResult tools, MCPProperties.GameMCPConfig config, GameStateSnapshot state) {
            StringBuilder sb = new StringBuilder();
            String modePrefix = lastFetchedGameName == null ? "" : detectedMode.getOrDefault(lastFetchedGameName, "");
            List<ToolSpecification> operationSpecs = tools.tools().keySet().stream()
                    .filter(spec -> !isStateTool(spec.name(), config))
                    .filter(spec -> isModeVisible(spec.name(), modePrefix))
                    .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                    .toList();
            List<ToolSpecification> visibleSpecs = operationSpecs.stream()
                    .filter(spec -> isOperationVisibleForState(spec.name(), state))
                    .toList();
            if (visibleSpecs.isEmpty()) {
                visibleSpecs = operationSpecs;
            }

            visibleSpecs.forEach(spec -> {
                if (isPlayCard(spec.name())) {
                    sb.append("- ")
                            .append(spec.name())
                            .append(": 打出一张手牌。args 使用 {\"card\":\"手牌名称\"}；")
                            .append("需要选择敌人的攻击牌再加 {\"target\":\"敌人 entity_id\"}；Self 目标牌不要传 target；")
                            .append("adapter 会按当前手牌自动转换为底层 MCP 参数。\n");
                    return;
                }
                sb.append("- ")
                        .append(spec.name())
                        .append(": ")
                        .append(spec.description() == null ? "" : spec.description())
                        .append("\n");
            });
            return sb.isEmpty() ? "(没有可用操作工具)" : sb.toString();
        }

        /**
         * 根据检测到的游戏模式过滤工具：SP 模式下隐藏 mp_* 工具，MP 模式下只显示 mp_* 工具。
         */
        private boolean isModeVisible(String toolName, String modePrefix) {
            if (toolName == null) {
                return false;
            }
            boolean isMpTool = toolName.startsWith(MP_PREFIX);
            if (MP_PREFIX.equals(modePrefix)) {
                return isMpTool;
            }
            return !isMpTool;
        }

        /**
         * 根据 state_type 判断某个操作工具是否应该暴露。
         */
        private boolean isOperationVisibleForState(String toolName, GameStateSnapshot state) {
            if (state == null || state.json() == null) {
                return true;
            }

            String name = removeModePrefix(toolName).toLowerCase();
            String stateType = state.stateType() == null ? "" : state.stateType().toLowerCase();
            return switch (stateType) {
                case "monster" -> isCombatTool(name);
                case "card_select" -> isCardSelectTool(name, state.json());
                case "card_reward" -> isRewardPickTool(name);
                case "rewards" -> isRewardClaimTool(name);
                case "map" -> isMapTool(name);
                case "event" -> isEventTool(name);
                case "shop" -> isShopTool(name);
                case "rest", "campfire" -> isRestTool(name);
                case "chest", "treasure" -> isChestTool(name);
                default -> true;
            };
        }

        /**
         * 去掉多人模式前缀，便于按通用工具名分类。
         */
        private String removeModePrefix(String toolName) {
            if (toolName == null) {
                return "";
            }
            return toolName.startsWith(MP_PREFIX) ? toolName.substring(MP_PREFIX.length()) : toolName;
        }

        /**
         * 战斗行动阶段只展示战斗和药水工具。
         */
        private boolean isCombatTool(String name) {
            return "combat_play_card".equals(name)
                    || "combat_end_turn".equals(name)
                    || "use_potion".equals(name);
        }

        /**
         * card_select 优先按战斗/牌堆选牌处理；只有状态里明确带奖励字段时才展示奖励选牌工具。
         */
        private boolean isCardSelectTool(String name, JsonNode root) {
            boolean rewardLike = root.has("rewards")
                    || root.has("card_reward")
                    || root.path("card_select").path("reward").asBoolean(false);
            if (rewardLike) {
                return isRewardPickTool(name);
            }
            boolean selectTool = name.contains("select_card")
                    || name.contains("confirm_selection")
                    || name.startsWith("deck_")
                    || name.startsWith("combat_select")
                    || name.startsWith("combat_confirm");
            if (root.has("card_select")) {
                return selectTool;
            }
            return selectTool || isRewardPickTool(name);
        }

        /**
         * 卡牌奖励界面只需要拿牌或跳过。
         */
        private boolean isRewardPickTool(String name) {
            return name.startsWith("rewards_pick")
                    || name.startsWith("rewards_skip")
                    || name.contains("pick_card")
                    || name.contains("skip_card");
        }

        /**
         * 奖励结算界面需要领取奖励或继续到地图。
         */
        private boolean isRewardClaimTool(String name) {
            return name.startsWith("rewards_")
                    || name.startsWith("claim_")
                    || name.contains("proceed")
                    || "use_potion".equals(name);
        }

        /**
         * 地图界面只展示地图选择和继续类工具。
         */
        private boolean isMapTool(String name) {
            return name.contains("map") || name.contains("node") || name.contains("proceed");
        }

        /**
         * 事件界面只展示事件选项和继续类工具。
         */
        private boolean isEventTool(String name) {
            return name.startsWith("event_") || name.contains("proceed");
        }

        /**
         * 商店界面只展示购买、删牌和离开商店工具。
         */
        private boolean isShopTool(String name) {
            return name.startsWith("shop_") || name.contains("purchase") || name.contains("remove") || name.contains("proceed");
        }

        /**
         * 火堆界面只展示休息、锻造等火堆操作。
         */
        private boolean isRestTool(String name) {
            return name.startsWith("rest_") || name.contains("campfire") || name.contains("proceed");
        }

        /**
         * 宝箱界面只展示领取遗物和继续类工具。
         */
        private boolean isChestTool(String name) {
            return name.contains("chest") || name.contains("relic") || name.contains("claim") || name.contains("proceed");
        }
    }

    /**
     * STS2 出牌计划编译器。
     * <p>
     * 该内部类只记录 agent 计划中的牌名语义，不判断费用或 can_play；真正能否执行交给 MCP 返回结果决定。
     */
    private class PlayCardPlanCompiler {
        /**
         * 准备单条操作的元数据。
         */
        private QueuedGameOperation prepareOperation(GameOperation operation, GameStateSnapshot plannedState) {
            QueuedGameOperation queued = QueuedGameOperation.from(operation, plannedState);
            if (!isPlayCard(operation.toolName())) {
                return queued;
            }

            String requestedCardName = readCardName(operation.args());
            if (!requestedCardName.isBlank()) {
                queued.metadata().put(META_PLANNED_CARD_NAME, requestedCardName);
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
         * 准备一批操作的元数据，同时模拟已经计划过的同名牌消耗，避免后续 index 取到旧位置。
         */
        private ArrayDeque<QueuedGameOperation> prepareBatch(List<GameOperation> operations, GameStateSnapshot plannedState) {
            ArrayDeque<QueuedGameOperation> queue = new ArrayDeque<>();
            List<JsonNode> mutableHand = mutableHandCopy(plannedState);

            for (GameOperation op : operations) {
                QueuedGameOperation queued = QueuedGameOperation.from(op, plannedState);
                if (isPlayCard(op.toolName())) {
                    String requestedCardName = readCardName(op.args());
                    if (!requestedCardName.isBlank()) {
                        queued.metadata().put(META_PLANNED_CARD_NAME, requestedCardName);
                        removeFirstCardByName(mutableHand, requestedCardName);
                    } else {
                        Integer cardIndex = readCardIndex(op.args());
                        if (cardIndex != null && cardIndex >= 0 && cardIndex < mutableHand.size()) {
                            JsonNode card = mutableHand.get(cardIndex);
                            if (!card.isMissingNode()) {
                                queued.metadata().put(META_PLANNED_CARD_NAME, card.path("name").asText(""));
                            }
                            mutableHand.remove(cardIndex.intValue());
                        }
                    }
                }

                queue.offerLast(queued);
            }
            return queue;
        }

        /**
         * 创建可变手牌副本，供批量计划模拟消耗使用。
         */
        private List<JsonNode> mutableHandCopy(GameStateSnapshot state) {
            List<JsonNode> result = new ArrayList<>();
            JsonNode hand = hand(state);
            if (hand.isArray()) {
                hand.forEach(result::add);
            }
            return result;
        }

        /**
         * 移除模拟手牌中的第一张同名牌。
         */
        private void removeFirstCardByName(List<JsonNode> cards, String cardName) {
            for (int i = 0; i < cards.size(); i++) {
                if (cardName.equals(cards.get(i).path("name").asText(""))) {
                    cards.remove(i);
                    return;
                }
            }
        }
    }

    /**
     * 判断最新状态是否仍然是玩家战斗出牌窗口。
     */
    private boolean isCombatPlayWindow(GameStateSnapshot state) {
        if (state == null || state.json() == null) {
            return false;
        }
        JsonNode battle = state.json().path("battle");
        return "monster".equals(state.stateType())
                && "player".equals(battle.path("turn").asText(""))
                && battle.path("is_play_phase").asBoolean(false);
    }

    /**
     * STS2 的第一版软错误策略：失败后只要仍处于玩家战斗出牌窗口，就认为单条指令失败但队列可继续。
     */
    @Override
    public boolean shouldContinueAfterOperationFailure(QueuedGameOperation operation,
                                                       GameStateSnapshot beforeState,
                                                       GameStateSnapshot afterState,
                                                       String reason) {
        return isCombatPlayWindow(afterState);
    }

    private String renderHandNames(JsonNode hand) {
        if (!hand.isArray() || hand.isEmpty()) {
            return "(空)";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode card : hand) {
            names.add(card.path("name").asText("(未知牌)"));
        }
        return String.join(", ", names);
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

    private String readCardName(JsonNode args) {
        if (args == null || args.isMissingNode() || args.isNull()) {
            return "";
        }
        for (String field : VIRTUAL_CARD_NAME_FIELDS) {
            JsonNode value = args.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return "";
    }

    private void removeVirtualCardNameFields(ObjectNode args) {
        for (String field : VIRTUAL_CARD_NAME_FIELDS) {
            args.remove(field);
        }
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

    private boolean isSelfTargetCard(JsonNode card) {
        if (card == null || !card.isObject()) {
            return false;
        }
        String targetType = card.path("target_type").asText("");
        return "self".equalsIgnoreCase(targetType);
    }

    private boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

}
