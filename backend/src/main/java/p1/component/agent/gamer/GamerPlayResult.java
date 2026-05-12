package p1.component.agent.gamer;

/**
 * gamer agent 单次决策的返回结果。
 *
 * @param message 面向用户的自然语言回复；模型未生成时为空字符串
 * @param result  桥接层的执行结果文本
 */
public record GamerPlayResult(String message, String result) {
    public static GamerPlayResult empty() {
        return new GamerPlayResult("", "");
    }
}
