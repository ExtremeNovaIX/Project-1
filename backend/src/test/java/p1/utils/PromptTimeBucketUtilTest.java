package p1.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptTimeBucketUtilTest {

    @Test
    void shouldFloorToQuarterHour() {
        LocalDateTime time = LocalDateTime.of(2026, 4, 29, 10, 44, 59);
        assertEquals("10:30", PromptTimeBucketUtil.formatQuarterHour(time));
    }

    @Test
    void shouldRenderRecentUserMessageAsLessThan15Minutes() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 29, 10, 44, 59);
        LocalDateTime previous = LocalDateTime.of(2026, 4, 29, 10, 35, 0);
        assertEquals("小于15分钟", PromptTimeBucketUtil.formatLastUserMessageTime(now, previous));
    }
}
