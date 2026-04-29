package p1.utils;

public class SessionUtil {
    /**
     * 标准化会话ID，如果为空或空白则返回默认值 "default"
     */
    public static String normalizeSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank()) ? "default" : sessionId.trim();
    }
}
