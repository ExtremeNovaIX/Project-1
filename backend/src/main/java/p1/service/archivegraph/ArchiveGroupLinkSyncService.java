package p1.service.archivegraph;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import p1.model.ArchiveLinkRecord;
import p1.model.document.MemoryArchiveDocument;
import p1.service.markdown.MemoryArchiveMarkdownService;
import p1.service.markdown.RecentEventGroupMarkdownService;

import java.util.List;

/**
 * 负责把 archive 级别的跨组链接映射并同步到 group 文档的双向链接中。
 * 比如当前两个组G1和G2，G1有A1和A2，G2有A3和A4。
 * 如果A1和A3在某次判定链接起来，那么就需要调用这里的方法，把A1和A3的双向链接写入G1和G2的双向链接中。
 */
@Service
@RequiredArgsConstructor
public class ArchiveGroupLinkSyncService {

    private final MemoryArchiveMarkdownService archiveService;
    private final RecentEventGroupMarkdownService recentEventGroupService;

    /**
     * 扫描当前组根节点上的链接，并把可映射的跨组关系写入 group 文档。
     */
    public void syncGroupLinks(String sessionId, String currentGroupId, List<MemoryArchiveDocument> archives) {
        if (archives == null || archives.isEmpty()) {
            return;
        }

        String normalizedCurrentGroupId = normalize(currentGroupId);
        if (normalizedCurrentGroupId.isBlank()) {
            return;
        }

        MemoryArchiveDocument rootArchive = archives.getFirst();
        if (rootArchive == null || rootArchive.getId() == null) {
            return;
        }

        MemoryArchiveDocument latestRoot = archiveService.findById(rootArchive.getId()).orElse(null);
        if (latestRoot == null || latestRoot.getLinks() == null || latestRoot.getLinks().isEmpty()) {
            return;
        }

        for (ArchiveLinkRecord link : latestRoot.getLinks()) {
            syncGroupLinkFromArchiveLink(sessionId, normalizedCurrentGroupId, link);
        }
    }

    /**
     * 将单条 archive 链接翻译为一条组级双向链接。
     */
    private void syncGroupLinkFromArchiveLink(String sessionId,
                                              String currentGroupId,
                                              ArchiveLinkRecord link) {
        if (link == null || link.getTargetArchiveId() == null) {
            return;
        }

        String relation = normalize(link.getRelation());
        String reverseRelation = reverseGroupRelation(relation);
        if (relation.isBlank() || reverseRelation.isBlank()) {
            return;
        }

        MemoryArchiveDocument targetArchive = archiveService.findById(link.getTargetArchiveId()).orElse(null);
        if (targetArchive == null) {
            return;
        }

        String targetGroupId = normalize(targetArchive.getGroupId());
        if (targetGroupId.isBlank() || targetGroupId.equals(currentGroupId)) {
            return;
        }

        recentEventGroupService.addGroupLink(
                sessionId,
                currentGroupId,
                targetGroupId,
                relation,
                reverseRelation,
                link.getConfidence(),
                link.getReason()
        );
    }

    /**
     * 返回组级关系的反向类型。
     */
    private String reverseGroupRelation(String relation) {
        return switch (normalize(relation)) {
            case "next_in_time" -> "previous_in_time";
            case "previous_in_time" -> "next_in_time";
            case ArchiveGraphRelations.RECENT_WINDOW_TARGETS -> ArchiveGraphRelations.RECENT_WINDOW_TARGETED_BY;
            case ArchiveGraphRelations.RECENT_WINDOW_TARGETED_BY -> ArchiveGraphRelations.RECENT_WINDOW_TARGETS;
            default -> "";
        };
    }

    /**
     * 对关系和 groupId 等文本做统一去空白处理。
     */
    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
