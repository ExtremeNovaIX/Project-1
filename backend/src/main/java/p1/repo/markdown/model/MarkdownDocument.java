package p1.repo.markdown.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record MarkdownDocument(Map<String, Object> frontmatter, String body) {
    public MarkdownDocument {
        frontmatter = frontmatter == null ? new LinkedHashMap<>() : new LinkedHashMap<>(frontmatter);
        body = body == null ? "" : body;
    }
}
