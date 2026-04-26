package p1.infrastructure.markdown.core;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FrontmatterReader {

    private final Map<?, ?> values;

    private FrontmatterReader(Map<?, ?> values) {
        this.values = values == null ? Map.of() : values;
    }

    public static FrontmatterReader of(Map<?, ?> values) {
        return new FrontmatterReader(values);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public String string(String key) {
        return stringValue(get(key));
    }

    public String rawString(String key) {
        Object value = get(key);
        return value == null ? "" : String.valueOf(value);
    }

    public Long longValue(String key) {
        return longValue(get(key));
    }

    public Integer intValue(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Integer.parseInt(text);
    }

    public Double doubleValue(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Double.parseDouble(text);
    }

    public LocalDateTime dateTime(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : LocalDateTime.parse(text);
    }

    public List<String> stringList(String key) {
        Object value = get(key);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .map(FrontmatterReader::stringValue)
                .filter(text -> !text.isBlank())
                .toList();
    }

    public List<Long> longList(String key) {
        Object value = get(key);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .map(FrontmatterReader::longValue)
                .filter(Objects::nonNull)
                .toList();
    }

    public static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.parseLong(text);
    }
}
