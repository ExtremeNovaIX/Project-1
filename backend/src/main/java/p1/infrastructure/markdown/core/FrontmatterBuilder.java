package p1.infrastructure.markdown.core;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FrontmatterBuilder {

    private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

    public FrontmatterBuilder put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public FrontmatterBuilder putNormalized(String key, String value) {
        values.put(key, normalize(value));
        return this;
    }

    public FrontmatterBuilder putDateTime(String key, LocalDateTime value) {
        values.put(key, value == null ? null : value.toString());
        return this;
    }

    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(values);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
