package p1.repo.markdown.model;

import p1.model.enums.DialogueMessageRole;

import java.time.LocalDateTime;

public record RawDialogueMessageRef(
        String notePath,
        String messageId,
        DialogueMessageRole role,
        LocalDateTime createdAt
) {
}
