package p1.infrastructure.mdc;

import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static p1.utils.SessionUtil.normalizeSessionId;

@Component
public class ChatSessionMetrics {

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
        TokenSnapshot current = TokenSnapshot.from(usage);
        counter.input.addAndGet(current.input());
        counter.output.addAndGet(current.output());
        counter.total.addAndGet(current.total());
        counter.cachedInput.addAndGet(current.cachedInput());
        return new TokenSnapshot(
                counter.input.get(),
                counter.output.get(),
                counter.total.get(),
                counter.cachedInput.get()
        );
    }

    public record TokenSnapshot(long input, long output, long total, long cachedInput) {

        public static TokenSnapshot from(TokenUsage usage) {
            if (usage == null) {
                return new TokenSnapshot(0L, 0L, 0L, 0L);
            }
            return new TokenSnapshot(
                    safeLong(usage.inputTokenCount()),
                    safeLong(usage.outputTokenCount()),
                    safeLong(usage.totalTokenCount()),
                    resolveCachedInputTokens(usage)
            );
        }

        public double cachedInputRate() {
            return input <= 0 ? 0.0 : (double) cachedInput / input;
        }

        public String cachedInputRatePercent() {
            return String.format(Locale.ROOT, "%.2f%%", cachedInputRate() * 100.0);
        }

        private static long resolveCachedInputTokens(TokenUsage usage) {
            if (!(usage instanceof OpenAiTokenUsage openAiTokenUsage)) {
                return 0L;
            }
            OpenAiTokenUsage.InputTokensDetails details = openAiTokenUsage.inputTokensDetails();
            return details == null ? 0L : safeLong(details.cachedTokens());
        }

        private static long safeLong(Integer value) {
            return value == null ? 0L : value.longValue();
        }
    }

    private static final class TokenCounter {
        private final AtomicLong input = new AtomicLong();
        private final AtomicLong output = new AtomicLong();
        private final AtomicLong total = new AtomicLong();
        private final AtomicLong cachedInput = new AtomicLong();
    }
}
