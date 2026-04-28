package p1.component.agent.context;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p1.model.ChatLogEntity;
import p1.service.ChatLogRepository;
import p1.utils.SessionUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 用来在向每次RpAgent请求中动态拼装时间上下文消息的组件
 */
@Component
@RequiredArgsConstructor
public class RpRequestTimeAppender {
    private static final DateTimeFormatter PROMPT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatLogRepository chatLogRepository;

    public ChatRequest augment(ChatRequest request, Object memoryId) {
        if (request == null || memoryId == null || request.messages().isEmpty()) {
            return request;
        }

        List<ChatMessage> messages = request.messages();
        int lastIndex = messages.size() - 1;
        ChatMessage lastMessage = messages.get(lastIndex);
        if (!(lastMessage instanceof UserMessage)) {
            return request;
        }

        String sessionId = SessionUtil.normalizeSessionId(memoryId.toString());
        List<ChatMessage> updatedMessages = new ArrayList<>(messages);
        updatedMessages.add(buildDynamicMemoryMessage(sessionId));
        return request.toBuilder()
                .messages(updatedMessages)
                .build();
    }

    private UserMessage buildDynamicMemoryMessage(String sessionId) {
        return UserMessage.builder()
                .name("time")
                .addContent(TextContent.from(buildDynamicMemoryContext(sessionId)))
                .build();
    }

    private String buildDynamicMemoryContext(String sessionId) {

        String currentTime = formatPromptTime(LocalDateTime.now());
        String lastUserMessageTime = findPreviousUserMessageTime(sessionId);
        String timeContext = "<time>\n当前时间：" + currentTime + "\n上次用户发消息时间：" + lastUserMessageTime + "\n</time>";
        return (timeContext.trim());
    }

    private String findPreviousUserMessageTime(String sessionId) {
        List<ChatLogEntity> userMessages =
                chatLogRepository.findTop2BySessionIdAndRoleOrderByCreatedAtDesc(sessionId, "USER");
        if (userMessages.size() < 2) {
            return "（暂无历史用户消息）";
        }
        LocalDateTime time = userMessages.get(1).getCreatedAt();
        return formatPromptTime(time);
    }

    private String formatPromptTime(@NonNull LocalDateTime time) {
        return time.format(PROMPT_TIME_FORMATTER);
    }
}
