package p1.repo.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import p1.model.ChatLogEntity;

import java.util.List;

@Repository
public interface ChatLogRepository extends JpaRepository<ChatLogEntity, Long> {
    List<ChatLogEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    void deleteBySessionId(String string);
}
