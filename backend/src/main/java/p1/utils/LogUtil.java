package p1.utils;

public class LogUtil {
    /**
     * 截取字符串头部最大maxLength长度
     */
    public static String trimHead(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "...(省略)" : text;
    }

    /**
     * 截取字符串尾部最大maxLength长度
     */
    public static String trimTail(String text, int maxLength) {
        return text.length() > maxLength ? "...(省略)" + text.substring(text.length() - maxLength) : text;
    }
}
