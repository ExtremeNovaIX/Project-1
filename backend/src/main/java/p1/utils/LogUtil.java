package p1.utils;

public class LogUtil {
    public static String trimTail(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "...(省略)" : text;
    }

    public static String trimHead(String text, int maxLength) {
        return text.length() > maxLength ? "...(省略)" + text.substring(text.length() - maxLength) : text;
    }
}
