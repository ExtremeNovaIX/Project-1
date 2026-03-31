package p1.component.ai.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p1.config.AssistantProperties;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class SummaryCacheManager {

    private final AssistantProperties props;
    private final Map<String, LinkedList<String>> summaryCache = new ConcurrentHashMap<>();

    public String getSummary(String sessionId) {
        List<String> queue = summaryCache.get(sessionId);
        if (queue == null || queue.isEmpty()) return "（暂无摘要）";

        StringBuilder sb = new StringBuilder("以下是之前的对话摘要：\n");
        for (int i = 0; i < queue.size(); i++) {
            sb.append(String.format("摘要 %d: %s\n", i + 1, queue.get(i)));
        }
        return sb.toString();
    }

    public void updateSummary(String sessionId, String newSummary) {
        summaryCache.computeIfAbsent(sessionId, k -> new LinkedList<>());
        LinkedList<String> queue = summaryCache.get(sessionId);
        if (queue.size() >= props.getChatMemory().getMaxMessages()) {
            queue.removeFirst();
        }
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("[yyyy-MM-dd HH:mm:ss]"));
        newSummary = timestamp + newSummary;
        queue.addLast(newSummary);
    }

    public void clear(String sessionId) {
        summaryCache.remove(sessionId);
    }
}