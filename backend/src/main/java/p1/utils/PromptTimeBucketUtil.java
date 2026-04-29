package p1.utils;

import lombok.NonNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class PromptTimeBucketUtil {

    private static final int MINUTES_PER_BUCKET = 15;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private PromptTimeBucketUtil() {
    }

    public static LocalDateTime floorToQuarterHour(@NonNull LocalDateTime time) {
        int flooredMinute = (time.getMinute() / MINUTES_PER_BUCKET) * MINUTES_PER_BUCKET;
        return time.withMinute(flooredMinute).withSecond(0).withNano(0);
    }

    public static String formatQuarterHour(@NonNull LocalDateTime time) {
        return floorToQuarterHour(time).format(TIME_FORMATTER);
    }

    public static String formatLastUserMessageTime(@NonNull LocalDateTime now,
                                                   @NonNull LocalDateTime previousUserMessageTime) {
        long minutes = Duration.between(previousUserMessageTime, now).toMinutes();
        if (minutes >= 0 && minutes < MINUTES_PER_BUCKET) {
            return "小于15分钟";
        }
        return formatQuarterHour(previousUserMessageTime);
    }
}
