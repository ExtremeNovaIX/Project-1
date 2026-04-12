package p1.service.markdown;

import org.springframework.stereotype.Component;
import p1.model.enums.DialogueMessageRole;
import p1.repo.markdown.model.DialogueBatchDocument;
import p1.repo.markdown.model.DialogueBatchMessage;
import p1.repo.markdown.model.MarkdownDocument;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DialogueBatchMarkdownMapper {

    public DialogueBatchDocument fromMarkdown(MarkdownDocument markdownDocument) {
        Map<String, Object> frontmatter = markdownDocument.frontmatter();
        return new DialogueBatchDocument(
                stringValue(frontmatter.get("id")),
                stringValue(frontmatter.get("session_id")),
                stringValue(frontmatter.get("status")),
                dateTimeValue(frontmatter.get("created_at")),
                dateTimeValue(frontmatter.get("updated_at")),
                dateTimeValue(frontmatter.get("processing_started_at")),
                messageList(frontmatter.get("messages"))
        );
    }

    public MarkdownDocument toMarkdown(DialogueBatchDocument document) {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("id", document.id());
        frontmatter.put("type", "dialogue_batch");
        frontmatter.put("session_id", document.sessionId());
        frontmatter.put("status", document.status());
        frontmatter.put("created_at", formatDateTime(document.createdAt()));
        frontmatter.put("updated_at", formatDateTime(document.updatedAt()));
        if (document.processingStartedAt() != null) {
            frontmatter.put("processing_started_at", formatDateTime(document.processingStartedAt()));
        }
        frontmatter.put("message_count", document.messageCount());
        frontmatter.put("messages", document.messages().stream().map(this::toMap).toList());
        frontmatter.put("tags", List.of("system/dialogue-batch"));

        String body = "# Dialogue Batch " + document.id() + "\n\n"
                + "System-owned transient batch state for session `" + document.sessionId() + "`.";
        return new MarkdownDocument(frontmatter, body);
    }

    private Map<String, Object> toMap(DialogueBatchMessage message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message_id", message.messageId());
        map.put("role", message.role().name());
        map.put("text", message.text());
        map.put("created_at", formatDateTime(message.createdAt()));
        map.put("raw_note_path", message.rawNotePath());
        map.put("raw_message_id", message.rawMessageId());
        return map;
    }

    private List<DialogueBatchMessage> messageList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<DialogueBatchMessage> messages = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) rawMap;
            messages.add(new DialogueBatchMessage(
                    stringValue(map.get("message_id")),
                    DialogueMessageRole.valueOf(stringValue(map.get("role"))),
                    stringValue(map.get("text")),
                    dateTimeValue(map.get("created_at")),
                    stringValue(map.get("raw_note_path")),
                    stringValue(map.get("raw_message_id"))
            ));
        }
        return messages;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private LocalDateTime dateTimeValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(text);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toString();
    }
}
