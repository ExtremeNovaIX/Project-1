package p1.mdc;

import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ChatSessionMetrics {

    private static final String UNKNOWN_SESSION_ID = "unknown";

    private final ConcurrentHashMap<String, AtomicInteger> roundCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenCounter> tokenCounters = new ConcurrentHashMap<>();

    public int incrementAndGetRound(String sessionId) {
        return roundCounters.computeIfAbsent(normalizeSessionId(sessionId), key -> new AtomicInteger())
                .incrementAndGet();
    }

    public int getCurrentRound(String sessionId) {
        AtomicInteger counter = roundCounters.get(normalizeSessionId(sessionId));
        return counter == null ? 0 : counter.get();
    }

    public TokenSnapshot addAndGetTokenTotals(String sessionId, TokenUsage usage) {
        TokenCounter counter = tokenCounters.computeIfAbsent(normalizeSessionId(sessionId), key -> new TokenCounter());
        if (usage != null) {
            counter.input.addAndGet(usage.inputTokenCount());
            counter.output.addAndGet(usage.outputTokenCount());
            counter.total.addAndGet(usage.totalTokenCount());
        }
        return new TokenSnapshot(counter.input.get(), counter.output.get(), counter.total.get());
    }

    public String normalizeSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank()) ? UNKNOWN_SESSION_ID : sessionId;
    }

    public record TokenSnapshot(long input, long output, long total) {
    }

    private static final class TokenCounter {
        private final AtomicLong input = new AtomicLong();
        private final AtomicLong output = new AtomicLong();
        private final AtomicLong total = new AtomicLong();
    }
}
