package p1.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import p1.config.prop.AssistantProperties;
import p1.infrastructure.vector.ArchiveVectorLibrary;
import p1.model.document.RecentEventGroupDocument;
import p1.service.markdown.RecentEventGroupMarkdownService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecentEventWindowMaintenanceService {

    private final AssistantProperties props;
    private final ArchiveEmbeddingService archiveEmbeddingService;
    private final RecentEventGroupMarkdownService recentEventGroupService;

    /**
     * 每小时扫描 recent-24h 窗口，删除过期的组。
     * 使用LRU策略淘汰过期的组。
     */
    @Scheduled(fixedDelayString = "${assistant.event-tree.recent-window-scan-fixed-delay-ms:3600000}")
    public void evictExpiredGroups() {
        LocalDateTime expireBefore = LocalDateTime.now().minusHours(props.getEventTree().getRecentWindowHours());
        List<RecentEventGroupDocument> groups = recentEventGroupService.findAll();

        for (RecentEventGroupDocument group : groups) {
            if (group == null || group.getSessionId() == null || group.getId() == null) {
                continue;
            }

            LocalDateTime lastHitAt = group.getLastHitAt();
            if (lastHitAt != null && !lastHitAt.isBefore(expireBefore)) {
                continue;
            }

            archiveEmbeddingService.deleteDocuments(
                    group.getSessionId(),
                    ArchiveVectorLibrary.RECENT_24H,
                    group.getRecentVectorDocumentIds()
            );
            recentEventGroupService.delete(group.getSessionId(), group.getId());

            log.info("[24h 维护] 已淘汰过期事件组，sessionId={}，groupId={}，lastHitAt={}",
                    group.getSessionId(), group.getId(), group.getLastHitAt());
        }
    }
}
