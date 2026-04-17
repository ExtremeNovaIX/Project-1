package p1.component.ai.assistant;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p1.model.ChatLogEntity;
import p1.repo.db.ChatLogRepository;
import p1.utils.SessionUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 用来在每次请求中动态拼装额外消息的组件
 */
@Component
@RequiredArgsConstructor
public class FrontendChatRequestAugmentor {

    private static final String DYNAMIC_MEMORY_NAME = "dynamic_memory";
    private static final String USER_ROLE = "USER";
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
                .name(DYNAMIC_MEMORY_NAME)
                .addContent(TextContent.from(buildDynamicMemoryContext(sessionId)))
                .build();
    }

    private String buildDynamicMemoryContext(String sessionId) {

        String currentTime = formatPromptTime(LocalDateTime.now());
        String lastUserMessageTime = findPreviousUserMessageTime(sessionId);
        String timeContext = "<time>\n当前时间：" + currentTime + "\n上次用户发消息时间：" + lastUserMessageTime + "\n</time>";
        return ("""
                以下是当前对话的动态消息，作为上下文补充：
                <dynamic_memory>
                %s
                </dynamic_memory>
                """.formatted(timeContext)).trim();
    }

    private String findPreviousUserMessageTime(String sessionId) {
        List<ChatLogEntity> userMessages =
                chatLogRepository.findTop2BySessionIdAndRoleOrderByCreatedAtDesc(sessionId, USER_ROLE);
        if (userMessages.size() < 2) {
            return "（暂无历史用户消息）";
        }
        ChatLogEntity previousUserMessage = userMessages.get(1);
        LocalDateTime time = previousUserMessage.getTime() != null
                ? previousUserMessage.getTime()
                : previousUserMessage.getCreatedAt();
        return formatPromptTime(time);
    }

    private String formatPromptTime(LocalDateTime time) {
        return time == null ? "（未知）" : time.format(PROMPT_TIME_FORMATTER);
    }
}
