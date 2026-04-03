package p1.utils;

import dev.langchain4j.data.message.*;

import java.util.List;
import java.util.stream.Collectors;

public class ChatMessageUtil {

    /**
     * 提取单条消息的纯文本内容
     *
     * @param message 要提取文本的消息对象
     * @return 消息的纯文本内容
     */
    public static String extractText(ChatMessage message) {
        switch (message) {
            case null -> {
                return "[N/A]";
            }
            case UserMessage userMsg -> {
                if (userMsg.hasSingleText()) {
                    return userMsg.singleText();
                } else {
                    return userMsg.contents().stream()
                            .filter(content -> content instanceof TextContent)
                            .map(content -> ((TextContent) content).text())
                            .collect(Collectors.joining("[非文本内容]"));
                }
            }
            case AiMessage aiMsg -> {
                if (aiMsg.hasToolExecutionRequests()) {
                    return aiMsg.toolExecutionRequests().toString();
                }
                return aiMsg.text() != null ? aiMsg.text() : "[N/A]";
            }
            case SystemMessage sysMsg -> {
                return sysMsg.text();
            }
            case ToolExecutionResultMessage toolMsg -> {
                return "工具执行结果 (" + toolMsg.toolName() + "): " + toolMsg.text();
            }
            default -> {
                return "未知的消息类型: " + message.type();
            }
        }
    }

    /**
     * 格式化消息列表为字符串
     *
     * @param messages 消息列表
     * @return 格式化后的消息列表字符串
     */
    public static String formatMessageList(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return "[N/A]";

        return messages.stream()
                .map(msg -> String.format("[%s]: %s", msg.type(), extractText(msg)))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 强转为指定类型的 Message，省去外部的 instanceof
     *
     * @param message     要强转的消息
     * @param targetClass 目标类型
     * @param <T>         目标类型
     * @return 转换后的消息，若类型不匹配则返回 null
     */
    public static <T extends ChatMessage> T castOrNull(ChatMessage message, Class<T> targetClass) {
        if (targetClass.isInstance(message)) {
            return targetClass.cast(message);
        }
        return null;
    }

    /**
     * 仅获取最后一条用户提问 (过滤掉 System prompt 和历史记录)
     *
     * @param messages 消息列表
     * @return 最后一条用户提问的文本内容
     */
    public static String getLastUserMessageText(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return "[N/A]";

        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                return extractText(messages.get(i));
            }
        }
        return "[N/A]";
    }
}