package p1.service.markdown;

import p1.model.document.RecentEventGroupDocument;

import java.util.List;
import java.util.Optional;

public interface RecentEventGroupStore {

    RecentEventGroupDocument save(RecentEventGroupDocument group);

    Optional<RecentEventGroupDocument> findById(String sessionId, String groupId);

    List<RecentEventGroupDocument> findAllBySessionId(String sessionId);

    List<RecentEventGroupDocument> findAll();

    void delete(String sessionId, String groupId);
}
