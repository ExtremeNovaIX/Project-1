package p1.infrastructure.markdown.model;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import p1.model.enums.MessageRole;

import java.time.LocalDateTime;

public record DialogueBatchMessage(
        String messageId,
        MessageRole role,
        String text,
        LocalDateTime createdAt,
        String rawNotePath,
        String rawMessageId
) {
    public ChatMessage toChatMessage() {
        return switch (role) {
            case USER -> UserMessage.from(text);
            case ASSISTANT -> AiMessage.from(text);
        };
    }
}
