package p1.service.markdown;

import org.springframework.stereotype.Component;
import p1.model.MemoryArchiveDocument;
import p1.repo.markdown.model.MarkdownDocument;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MemoryArchiveMarkdownMapper {

    private static final String RETRIEVAL_SUMMARY_HEADER = "## Retrieval Summary";

    public MemoryArchiveDocument fromMarkdown(MarkdownDocument document) {
        Map<String, Object> frontmatter = document.frontmatter();
        BodySections bodySections = parseBody(document.body());

        MemoryArchiveDocument archive = new MemoryArchiveDocument();
        archive.setId(longValue(frontmatter.get("legacy_id")));
        archive.setSessionId(stringValue(frontmatter.get("session_id")));
        archive.setCategory(stringValue(frontmatter.get("category")));
        archive.setKeywordSummary(bodySections.keywordSummary());
        archive.setDetailedSummary(bodySections.detailedSummary());
        archive.setCreatedAt(dateTimeValue(frontmatter.get("created_at")));
        archive.setUpdatedAt(dateTimeValue(frontmatter.get("updated_at")));
        archive.setMergeCount(intValue(frontmatter.get("merge_count"), 0));
        archive.setSourceFragmentIds(List.of());
        return archive;
    }

    public MarkdownDocument toMarkdown(MemoryArchiveDocument archive) {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("id", noteId(archive.getId()));
        frontmatter.put("type", "memory_archive");
        frontmatter.put("legacy_id", archive.getId());
        frontmatter.put("session_id", archive.getSessionId());
        frontmatter.put("title", buildTitle(archive));
        frontmatter.put("category", normalize(archive.getCategory()));
        frontmatter.put("created_at", formatDateTime(archive.getCreatedAt()));
        frontmatter.put("updated_at", formatDateTime(archive.getUpdatedAt()));
        frontmatter.put("merge_count", archive.getMergeCount() == null ? 0 : archive.getMergeCount());
        frontmatter.put("source_refs", List.of());
        frontmatter.put("pending_patch_ids", List.of());
        frontmatter.put("tags", List.of("wiki/memory"));

        StringBuilder body = new StringBuilder();
        body.append("# ").append(buildTitle(archive)).append("\n\n");

        String detailedSummary = normalize(archive.getDetailedSummary());
        if (!detailedSummary.isBlank()) {
            body.append(detailedSummary).append("\n\n");
        }

        String keywordSummary = normalize(archive.getKeywordSummary());
        if (!keywordSummary.isBlank()) {
            body.append(RETRIEVAL_SUMMARY_HEADER).append("\n\n")
                    .append(keywordSummary)
                    .append("\n");
        }

        return new MarkdownDocument(frontmatter, body.toString().trim());
    }

    public String noteId(Long id) {
        return String.format("memory-%05d", id);
    }

    public String buildTitle(MemoryArchiveDocument archive) {
        String category = normalize(archive == null ? null : archive.getCategory());
        return category.isBlank() ? noteId(archive == null ? 0L : archive.getId()) : category;
    }

    private BodySections parseBody(String body) {
        String normalizedBody = normalizeBody(body);
        if (normalizedBody.startsWith("# ")) {
            int titleEnd = normalizedBody.indexOf("\n\n");
            if (titleEnd >= 0) {
                normalizedBody = normalizedBody.substring(titleEnd + 2).strip();
            }
        }

        int retrievalIndex = normalizedBody.indexOf(RETRIEVAL_SUMMARY_HEADER);
        if (retrievalIndex < 0) {
            return new BodySections(normalizedBody, "");
        }

        String detailedSummary = normalizedBody.substring(0, retrievalIndex).strip();
        String keywordSummary = normalizedBody.substring(retrievalIndex + RETRIEVAL_SUMMARY_HEADER.length()).strip();
        int nextSectionIndex = keywordSummary.indexOf("\n## ");
        if (nextSectionIndex >= 0) {
            keywordSummary = keywordSummary.substring(0, nextSectionIndex).strip();
        }
        return new BodySections(detailedSummary, keywordSummary);
    }

    private String normalizeBody(String body) {
        return body == null ? "" : body.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.parseLong(text);
    }

    private int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : Integer.parseInt(text);
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

    private record BodySections(String detailedSummary, String keywordSummary) {
    }
}
