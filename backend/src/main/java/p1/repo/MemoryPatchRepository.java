package p1.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import p1.model.MemoryPatchEntity;

import java.util.List;

@Repository
public interface MemoryPatchRepository extends JpaRepository<MemoryPatchEntity, Long> {

    List<MemoryPatchEntity> findByTargetMemoryIdAndCompressedFalseOrderByCreatedAtAsc(Long targetMemoryId);

    long countByTargetMemoryIdAndCompressedFalse(Long targetMemoryId);

    long countByCompressedFalse();

    @Query("select distinct p.targetMemoryId from MemoryPatchEntity p where p.compressed = false")
    List<Long> findDistinctTargetMemoryIdsByCompressedFalse();
}
