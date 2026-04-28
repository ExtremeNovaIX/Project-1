package p1.service.markdown;

import p1.model.document.MemoryArchiveDocument;

import java.util.List;
import java.util.Optional;

public interface MemoryArchiveStore {

    boolean existsById(Long id);

    Optional<MemoryArchiveDocument> findById(Long id);

    List<MemoryArchiveDocument> findAllOrderByIdAsc();

    List<MemoryArchiveDocument> findAllOrderByIdAsc(String sessionId);

    MemoryArchiveDocument save(MemoryArchiveDocument archive);

    String noteId(Long id);

    String displayTitle(MemoryArchiveDocument archive);

    String relativeNotePath(MemoryArchiveDocument archive);
}
