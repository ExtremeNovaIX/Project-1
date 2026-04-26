package p1.service.archive.graph;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import p1.model.document.MemoryArchiveDocument;
import p1.service.archive.recentwindow.RecentWindowLinker;

import java.util.List;

/**
 * archive 图补全的总入口。
 * 负责串起组内时间边、recent-window 连边和组级链接同步。
 */
@Service
@RequiredArgsConstructor
public class ArchiveGraphService {

    private final ArchiveLinkService archiveLinkService;
    private final RecentWindowLinker recentWindowLinker;
    private final ArchiveGroupLinkSyncService groupLinkSyncService;

    /**
     * 为一组新写入的 archive 补全事件图结构。
     */
    public void enrichGroupGraph(String sessionId, List<MemoryArchiveDocument> archives, List<String> currentGroupTags) {
        if (archives == null || archives.isEmpty()) {
            return;
        }

        archiveLinkService.linkGroupTimeLine(sessionId, archives);
        recentWindowLinker.linkRoot(sessionId, archives, currentGroupTags);
    }

    /**
     * 根据 archive 上已有的跨组边，回写对应的组级双向链接。
     */
    public void syncGroupLinks(String sessionId, String currentGroupId, List<MemoryArchiveDocument> archives) {
        groupLinkSyncService.syncGroupLinks(sessionId, currentGroupId, archives);
    }

    /**
     * 使用默认组标签执行 recent-window 连边。
     */
    public void linkRecentWindow(String sessionId, List<MemoryArchiveDocument> archives) {
        linkRecentWindow(sessionId, archives, List.of());
    }

    /**
     * 为当前组 root 补一条 recent-window 跨组连接边。
     */
    public void linkRecentWindow(String sessionId,
                                 List<MemoryArchiveDocument> archives,
                                 List<String> currentGroupTags) {
        recentWindowLinker.linkRoot(sessionId, archives, currentGroupTags);
    }
}
