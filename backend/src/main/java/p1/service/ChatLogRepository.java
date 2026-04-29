package p1.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import p1.model.ChatLogEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatLogRepository extends JpaRepository<ChatLogEntity, Long> {
    List<ChatLogEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ChatLogEntity> findTop2BySessionIdAndRoleOrderByCreatedAtDesc(String sessionId, String role);

    Optional<ChatLogEntity> findFirstBySessionIdAndRoleOrderByCreatedAtDesc(String sessionId, String role);

    void deleteBySessionId(String string);
}
