package p1.component.agent.gamer.bridge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * 游戏智能体与角色扮演智能体之间的通信桥梁。
 * <p>
 * 当游戏智能体执行值得关注的操作时（通过 notifyRP 工具），
 * 事件落入每个会话的队列中。面向用户的聊天流程随后提取这些事件
 * 并传递给角色扮演智能体进行角色化叙述。
 */
@Component
@Slf4j
public class GamerRPBridge {

    private final Map<String, BlockingDeque<GameEvent>> eventQueues = new ConcurrentHashMap<>();

    /**
     * 将游戏事件入队到指定游戏会话。
     *
     * @param sessionKey  会话 key
     * @param description 关键游戏事件描述
     */
    public void enqueueGameEvent(String sessionKey, String description) {
        String key = normalizeKey(sessionKey);
        // 每个会话单独维护事件队列，避免不同游戏会话的叙事事件串台。
        BlockingDeque<GameEvent> queue = eventQueues.computeIfAbsent(key,
                k -> new LinkedBlockingDeque<>(128));
        GameEvent event = new GameEvent(System.currentTimeMillis(), description);
        if (!queue.offerLast(event)) {
            log.warn("[游戏桥接] 事件队列已满，丢弃事件: {}", description.substring(0, Math.min(100, description.length())));
        }
    }

    /**
     * 提取某个会话的所有待处理游戏事件，格式化为适合注入角色扮演智能体上下文的字符串。
     * 如果没有待处理事件，返回 null。
     *
     * @param sessionKey 会话 key
     * @return 事件列表文本；没有事件时返回 null
     */
    public String drainEventsForRP(String sessionKey) {
        BlockingDeque<GameEvent> queue = eventQueues.get(normalizeKey(sessionKey));
        if (queue == null || queue.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        GameEvent event;
        // 提取事件时直接清空队列，防止同一事件被重复叙述。
        while ((event = queue.pollFirst()) != null) {
            sb.append("- ").append(event.description).append("\n");
        }
        return sb.toString();
    }

    /**
     * 检查某个会话是否有待处理的事件。
     *
     * @param sessionKey 会话 key
     * @return true 表示存在待处理事件
     */
    public boolean hasEvents(String sessionKey) {
        BlockingDeque<GameEvent> queue = eventQueues.get(normalizeKey(sessionKey));
        return queue != null && !queue.isEmpty();
    }

    /**
     * 归一化事件队列 key。
     *
     * @param sessionKey 原始会话 key
     * @return 可用于 map 的非空 key
     */
    private String normalizeKey(String sessionKey) {
        return sessionKey == null || sessionKey.isBlank() ? "default" : sessionKey;
    }

    /**
     * 游戏事件记录。
     *
     * @param timestamp   事件入队时间
     * @param description 事件描述
     */
    public record GameEvent(long timestamp, String description) {
    }
}
