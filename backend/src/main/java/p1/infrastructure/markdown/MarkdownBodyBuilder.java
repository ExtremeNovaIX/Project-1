package p1.infrastructure.markdown;

public final class MarkdownBodyBuilder {

    private final StringBuilder body = new StringBuilder();

    public MarkdownBodyBuilder title(String title) {
        body.append("# ").append(normalize(title)).append("\n\n");
        return this;
    }

    public MarkdownBodyBuilder paragraph(String text) {
        String normalized = normalize(text);
        if (!normalized.isBlank()) {
            body.append(normalized).append("\n\n");
        }
        return this;
    }

    public MarkdownBodyBuilder section(String header, String content) {
        String normalized = normalize(content);
        if (!normalized.isBlank()) {
            body.append(header).append("\n\n")
                    .append(normalized)
                    .append("\n\n");
        }
        return this;
    }

    public MarkdownBodyBuilder sectionHeader(String header) {
        body.append(header).append("\n\n");
        return this;
    }

    public MarkdownBodyBuilder line(String line) {
        body.append(line == null ? "" : line).append("\n");
        return this;
    }

    public MarkdownBodyBuilder blankLine() {
        body.append("\n");
        return this;
    }

    public String build() {
        return body.toString().trim();
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
