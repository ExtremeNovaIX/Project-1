package p1.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import p1.model.MemoryArchiveEntity;

public interface MemoryArchiveRepository extends JpaRepository<MemoryArchiveEntity, Long> {
}