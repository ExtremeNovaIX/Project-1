package p1.service.ai.frontend.memory;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SummaryCacheManager {

    private final Map<String, String> summaryCache = new ConcurrentHashMap<>();

    public String getSummary(String sessionId) {
        return summaryCache.getOrDefault(sessionId, "（暂无记忆摘要）");
    }

    public void updateSummary(String sessionId, String newSummary) {
        summaryCache.put(sessionId, newSummary);
    }

    public void clear(String sessionId) {
        summaryCache.remove(sessionId);
    }
}