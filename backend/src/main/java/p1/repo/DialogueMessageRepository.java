package p1.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import p1.model.DialogueMessageEntity;
import p1.model.enums.DialogueMemoryStatus;

import java.util.List;

@Repository
public interface DialogueMessageRepository extends JpaRepository<DialogueMessageEntity, Long> {

    List<DialogueMessageEntity> findBySessionIdAndMemoryStatusOrderByCreatedAtAsc(
            String sessionId,
            DialogueMemoryStatus memoryStatus
    );

    List<DialogueMessageEntity> findByBatchIdOrderByCreatedAtAsc(Long batchId);

    List<DialogueMessageEntity> findByMemoryStatusAndBatchIdIsNullOrderByCreatedAtAsc(DialogueMemoryStatus memoryStatus);

    @Query("select distinct d.batchId from DialogueMessageEntity d where d.batchId is not null and d.memoryStatus in ('PENDING', 'PROCESSING') order by d.batchId asc")
    List<Long> findUnfinishedBatchIds();

    @Query("select coalesce(max(d.batchId), 0) from DialogueMessageEntity d")
    Long findMaxBatchId();
}
