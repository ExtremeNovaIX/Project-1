package p1.infrastructure.markdown.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理raw batch的文档，表示某个批次原始消息的状态
 */
public record RawBatchDocument(
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
