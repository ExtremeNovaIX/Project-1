package p1.infrastructure.markdown.model;

import p1.model.enums.MessageRole;

import java.time.LocalDateTime;

public record RawDialogueMessageRef(
        String notePath,
        String messageId,
        MessageRole role,
        LocalDateTime createdAt
) {
}
