package p1.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimedMessageUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})] (.*)$", Pattern.DOTALL);

    private TimedMessageUtil() {
    }

    public static String prefix(LocalDateTime time, String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (TIMESTAMP_PATTERN.matcher(normalized).matches()) {
            return normalized;
        }
        LocalDateTime actualTime = time == null ? LocalDateTime.now() : time;
        return "[" + actualTime.format(FORMATTER) + "] " + normalized;
    }

    public static LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = TIMESTAMP_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return LocalDateTime.parse(matcher.group(1), FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
