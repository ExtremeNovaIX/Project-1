package p1.service;

import org.junit.jupiter.api.Test;
import p1.config.prop.AssistantProperties;
import p1.infrastructure.vector.ArchiveVectorLibrary;
import p1.model.document.RecentEventGroupDocument;
import p1.service.markdown.RecentEventGroupMarkdownService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

class RecentEventWindowMaintenanceServiceTest {

    @Test
    void shouldDeleteOnlyExpiredRecentGroups() {
        AssistantProperties props = new AssistantProperties();
        props.getEventTree().setRecentWindowHours(24);

        ArchiveEmbeddingService archiveEmbeddingService = mock(ArchiveEmbeddingService.class);
        RecentEventGroupMarkdownService recentEventGroupService = mock(RecentEventGroupMarkdownService.class);

        RecentEventGroupDocument expired = new RecentEventGroupDocument();
        expired.setId("group-expired");
        expired.setSessionId("s1");
        expired.setLastHitAt(LocalDateTime.now().minusHours(25));
        expired.setRecentVectorDocumentIds(List.of("recent-1", "recent-2"));

        RecentEventGroupDocument active = new RecentEventGroupDocument();
        active.setId("group-active");
        active.setSessionId("s1");
        active.setLastHitAt(LocalDateTime.now().minusHours(2));
        active.setRecentVectorDocumentIds(List.of("recent-3"));

        when(recentEventGroupService.findAll()).thenReturn(List.of(expired, active));

        RecentEventWindowMaintenanceService service = new RecentEventWindowMaintenanceService(
                props,
                archiveEmbeddingService,
                recentEventGroupService
        );

        service.evictExpiredGroups();

        verify(archiveEmbeddingService).deleteDocuments("s1", ArchiveVectorLibrary.RECENT_24H, List.of("recent-1", "recent-2"));
        verify(recentEventGroupService).delete("s1", "group-expired");
        verify(archiveEmbeddingService, never()).deleteDocuments("s1", ArchiveVectorLibrary.RECENT_24H, List.of("recent-3"));
    }
}
