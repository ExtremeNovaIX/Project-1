package p1.infrastructure.markdown;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MarkdownSections {

    private final String preamble;
    private final Map<String, String> sections;

    private MarkdownSections(String preamble, Map<String, String> sections) {
        this.preamble = preamble == null ? "" : preamble;
        this.sections = sections == null ? Map.of() : Map.copyOf(sections);
    }

    public static MarkdownSections parse(String body, List<String> headers) {
        String normalizedBody = stripTitle(normalizeBody(body));
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (String header : headers) {
            indexes.put(header, sectionHeaderIndex(normalizedBody, header));
        }

        int firstSectionIndex = firstPositiveIndex(indexes.values().stream().mapToInt(Integer::intValue).toArray());
        String preamble = firstSectionIndex < 0 ? normalizedBody : normalizedBody.substring(0, firstSectionIndex).strip();

        Map<String, String> sections = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : indexes.entrySet()) {
            sections.put(entry.getKey(), sectionContent(normalizedBody, entry.getKey(), entry.getValue(), indexes));
        }
        return new MarkdownSections(preamble, sections);
    }

    public String preamble() {
        return preamble;
    }

    public String content(String header) {
        return sections.getOrDefault(header, "");
    }

    private static String stripTitle(String body) {
        if (!body.startsWith("# ")) {
            return body;
        }
        int titleEnd = body.indexOf("\n\n");
        if (titleEnd < 0) {
            return "";
        }
        return body.substring(titleEnd + 2).strip();
    }

    private static String sectionContent(String body,
                                         String header,
                                         int headerIndex,
                                         Map<String, Integer> siblingIndexes) {
        if (headerIndex < 0) {
            return "";
        }

        int contentStart = headerIndex + header.length();
        int contentEnd = body.length();
        for (int siblingIndex : siblingIndexes.values()) {
            if (siblingIndex > headerIndex && siblingIndex < contentEnd) {
                contentEnd = siblingIndex;
            }
        }
        return body.substring(contentStart, contentEnd).strip();
    }

    private static int sectionHeaderIndex(String body, String header) {
        int fromIndex = 0;
        while (fromIndex < body.length()) {
            int index = body.indexOf(header, fromIndex);
            if (index < 0) {
                return -1;
            }

            boolean lineStart = index == 0 || body.charAt(index - 1) == '\n';
            int afterHeader = index + header.length();
            boolean lineEnd = afterHeader == body.length() || body.charAt(afterHeader) == '\n';
            if (lineStart && lineEnd) {
                return index;
            }

            fromIndex = index + header.length();
        }
        return -1;
    }

    private static int firstPositiveIndex(int... indexes) {
        int result = -1;
        for (int index : indexes) {
            if (index < 0) {
                continue;
            }
            if (result < 0 || index < result) {
                result = index;
            }
        }
        return result;
    }

    private static String normalizeBody(String body) {
        return body == null ? "" : body.replace("\r\n", "\n").replace('\r', '\n').strip();
    }
}
