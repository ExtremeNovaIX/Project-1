package p1.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import p1.model.MemoryPatchEntity;
import java.util.List;

@Repository
public interface MemoryPatchRepository extends JpaRepository<MemoryPatchEntity, Long> {
    List<MemoryPatchEntity> findByTargetMemoryIdOrderByCreatedAtAsc(Long targetMemoryId);
}