package p1.service.markdown;

import p1.infrastructure.markdown.model.RawBatchDocument;

import java.util.Optional;
import java.util.Set;

public interface DialogueBatchStore {

    Optional<RawBatchDocument> findCollecting(String sessionId);

    Optional<RawBatchDocument> findProcessing(String sessionId);

    void saveCollecting(String sessionId, RawBatchDocument document);

    void saveProcessing(String sessionId, RawBatchDocument document);

    void deleteCollecting(String sessionId);

    void deleteProcessing(String sessionId);

    Set<String> listSessionIdsWithOpenBatches();
}
