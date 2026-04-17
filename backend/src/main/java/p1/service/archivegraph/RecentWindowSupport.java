package p1.service.archivegraph;

import p1.model.document.MemoryArchiveDocument;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * recent-window 包内共享的文本和 key 工具。
 */
final class RecentWindowSupport {

    private RecentWindowSupport() {
    }

    static String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    static Set<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String value = normalize(tag).toLowerCase(Locale.ROOT);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    static String searchableArchiveText(MemoryArchiveDocument archive) {
        if (archive == null) {
            return "";
        }

        return String.join("\n",
                        normalize(archive.getTopic()),
                        normalize(archive.getKeywordSummary()),
                        normalize(archive.getNarrative()))
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 优先按组id聚合，如果没有则按 archive id聚合。
     */
    static String candidateKey(String groupId, Long targetArchiveId) {
        String normalizedGroupId = normalize(groupId);
        if (!normalizedGroupId.isBlank()) {
            return "group:" + normalizedGroupId;
        }
        return "archive:" + targetArchiveId;
    }
}
