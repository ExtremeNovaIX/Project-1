package p1.service.markdown;

import org.springframework.stereotype.Component;
import p1.model.enums.DialogueMessageRole;
import p1.repo.markdown.model.MarkdownDocument;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RawDialogueMarkdownAssembler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public MarkdownDocument createDailyNote(LocalDate date) {
        return new MarkdownDocument(new LinkedHashMap<>(),
                "# Dialogue " + DATE_FORMATTER.format(date) + "\n\n"
                        + "> System-owned raw dialogue log.\n");
    }

    public MarkdownDocument appendMessage(MarkdownDocument note,
                                          String sessionId,
                                          DialogueMessageRole role,
                                          String cleanText,
                                          LocalDateTime timestamp,
                                          String messageId) {
        LocalDate date = timestamp.toLocalDate();
        Map<String, Object> frontmatter = new LinkedHashMap<>(note.frontmatter());
        frontmatter.put("id", "raw-dialogue-" + sessionId + "-" + DATE_FORMATTER.format(date));
        frontmatter.put("type", "raw_dialogue_day");
        frontmatter.put("session_id", sessionId);
        frontmatter.put("date", DATE_FORMATTER.format(date));
        frontmatter.put("llm_access", "read_only");
        frontmatter.put("message_count", incrementCount(frontmatter.get("message_count")));
        frontmatter.put("tags", List.of("raw/dialogue", "daily"));

        StringBuilder body = new StringBuilder(note.body().stripTrailing());
        if (!body.isEmpty()) {
            body.append("\n\n");
        }
        body.append("## ")
                .append(TIME_FORMATTER.format(timestamp))
                .append(" ")
                .append(role.name())
                .append("\n")
                .append(cleanText)
                .append("\n^")
                .append(messageId)
                .append("\n");

        return new MarkdownDocument(frontmatter, body.toString());
    }

    private int incrementCount(Object currentValue) {
        if (currentValue instanceof Number number) {
            return number.intValue() + 1;
        }
        return 1;
    }
}
