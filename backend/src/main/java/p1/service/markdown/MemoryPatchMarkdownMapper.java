package p1.service.markdown;

import org.springframework.stereotype.Component;
import p1.model.MemoryPatchDocument;
import p1.repo.markdown.model.MarkdownDocument;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MemoryPatchMarkdownMapper {

    public MemoryPatchDocument fromMarkdown(MarkdownDocument document) {
        Map<String, Object> frontmatter = document.frontmatter();

        MemoryPatchDocument patch = new MemoryPatchDocument();
        patch.setId(longValue(frontmatter.get("legacy_id")));
        patch.setTargetMemoryId(longValue(frontmatter.get("target_memory_id")));
        patch.setCorrectionContent(parseBody(document.body()));
        patch.setCreatedAt(dateTimeValue(frontmatter.get("created_at")));
        patch.setCompressed(boolValue(frontmatter.get("compressed")));
        patch.setCompressedAt(dateTimeValue(frontmatter.get("compressed_at")));
        return patch;
    }

    public MarkdownDocument toMarkdown(MemoryPatchDocument patch, String targetNoteId, String targetLinkPath, String targetLabel) {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("id", noteId(patch.getId()));
        frontmatter.put("type", "memory_patch");
        frontmatter.put("legacy_id", patch.getId());
        frontmatter.put("target_memory_id", patch.getTargetMemoryId());
        if (targetNoteId != null && !targetNoteId.isBlank()) {
            frontmatter.put("target_note_id", targetNoteId);
        }
        frontmatter.put("created_at", formatDateTime(patch.getCreatedAt()));
        frontmatter.put("compressed", Boolean.TRUE.equals(patch.getCompressed()));
        if (patch.getCompressedAt() != null) {
            frontmatter.put("compressed_at", formatDateTime(patch.getCompressedAt()));
        }
        frontmatter.put("source_refs", List.of());
        frontmatter.put("tags", List.of("memory/patch"));

        StringBuilder body = new StringBuilder();
        body.append("# Patch ").append(noteId(patch.getId())).append("\n\n");
        if (targetLinkPath != null && !targetLinkPath.isBlank() && targetLabel != null && !targetLabel.isBlank()) {
            body.append("Target: [[")
                    .append(targetLinkPath)
                    .append("|")
                    .append(targetLabel)
                    .append("]]\n\n");
        } else if (patch.getTargetMemoryId() != null) {
            body.append("Target memory ID: ").append(patch.getTargetMemoryId()).append("\n\n");
        }

        body.append(normalize(patch.getCorrectionContent())).append("\n");
        return new MarkdownDocument(frontmatter, body.toString().trim());
    }

    public String noteId(Long id) {
        return String.format("patch-%05d", id);
    }

    private String parseBody(String body) {
        String normalized = body == null ? "" : body.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.startsWith("# ")) {
            int titleEnd = normalized.indexOf("\n\n");
            if (titleEnd >= 0) {
                normalized = normalized.substring(titleEnd + 2).strip();
            }
        }
        if (normalized.startsWith("Target: ") || normalized.startsWith("Target memory ID: ")) {
            int targetEnd = normalized.indexOf("\n\n");
            if (targetEnd >= 0) {
                normalized = normalized.substring(targetEnd + 2).strip();
            }
        }
        return normalized;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.parseLong(text);
    }

    private boolean boolValue(Object value) {
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private LocalDateTime dateTimeValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : LocalDateTime.parse(text);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
