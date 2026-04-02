package p1.utils;

public class LogUtil {
    public static String summarize(String text,int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
