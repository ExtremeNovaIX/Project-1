package p1.mdc;

import dev.langchain4j.model.openai.OpenAiTokenUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatSessionMetricsTest {

    @Test
    void shouldTrackCachedInputTokensAndRate() {
        ChatSessionMetrics metrics = new ChatSessionMetrics();

        OpenAiTokenUsage usage = OpenAiTokenUsage.builder()
                .inputTokenCount(200)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(50)
                        .build())
                .outputTokenCount(80)
                .totalTokenCount(280)
                .build();

        ChatSessionMetrics.TokenSnapshot current = ChatSessionMetrics.TokenSnapshot.from(usage);
        ChatSessionMetrics.TokenSnapshot total = metrics.addAndGetTokenTotals("session-1", usage);

        assertEquals(200L, current.input());
        assertEquals(80L, current.output());
        assertEquals(280L, current.total());
        assertEquals(50L, current.cachedInput());
        assertEquals(0.25d, current.cachedInputRate());
        assertEquals("25.00%", current.cachedInputRatePercent());

        assertEquals(200L, total.input());
        assertEquals(80L, total.output());
        assertEquals(280L, total.total());
        assertEquals(50L, total.cachedInput());
        assertEquals("25.00%", total.cachedInputRatePercent());
    }

    @Test
    void shouldTreatNonOpenAiUsageAsZeroCachedTokens() {
        ChatSessionMetrics metrics = new ChatSessionMetrics();

        ChatSessionMetrics.TokenSnapshot total = metrics.addAndGetTokenTotals(
                "session-1",
                new dev.langchain4j.model.output.TokenUsage(120, 30, 150)
        );

        assertEquals(120L, total.input());
        assertEquals(30L, total.output());
        assertEquals(150L, total.total());
        assertEquals(0L, total.cachedInput());
        assertEquals(0.0d, total.cachedInputRate());
        assertEquals("0.00%", total.cachedInputRatePercent());
    }
}
