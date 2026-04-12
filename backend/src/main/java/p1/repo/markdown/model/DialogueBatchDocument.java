package p1.repo.markdown.model;

import java.time.LocalDateTime;
import java.util.List;

public record DialogueBatchDocument(
        String id,
        String sessionId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime processingStartedAt,
        List<DialogueBatchMessage> messages
) {
    public int messageCount() {
        return messages == null ? 0 : messages.size();
    }
}
