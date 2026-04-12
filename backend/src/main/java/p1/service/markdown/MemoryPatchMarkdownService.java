package p1.service.markdown;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p1.config.prop.AssistantProperties;
import p1.model.MemoryArchiveDocument;
import p1.model.MemoryPatchDocument;
import p1.repo.markdown.MemoryPatchMarkdownRepository;
import p1.repo.markdown.model.MarkdownDocument;
import p1.utils.SessionUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryPatchMarkdownService {

    private final AssistantProperties props;
    private final MemoryPatchMarkdownRepository repository;
    private final MemoryPatchMarkdownMapper mapper;
    private final MemoryArchiveMarkdownService archiveService;

    private final ConcurrentHashMap<Long, Path> locations = new ConcurrentHashMap<>();
    private final Object indexLock = new Object();
    private final AtomicBoolean indexLoaded = new AtomicBoolean(false);
    private final AtomicLong sequence = new AtomicLong(0);

    public MemoryPatchDocument save(MemoryPatchDocument patch) {
        ensureIndexLoaded();

        boolean isNew = patch.getId() == null;
        if (isNew) {
            patch.setId(sequence.incrementAndGet());
            patch.setCreatedAt(LocalDateTime.now());
            if (patch.getCompressed() == null) {
                patch.setCompressed(false);
            }
        }
        if (patch.getCompressed() == null) {
            patch.setCompressed(false);
        }

        MemoryArchiveDocument targetArchive = archiveService.findById(patch.getTargetMemoryId()).orElse(null);
        String sessionId = SessionUtil.normalizeSessionId(targetArchive == null ? null : targetArchive.getSessionId());
        Path currentPath = locations.get(patch.getId());
        Path targetPath = resolveTargetPath(sessionId, patch);

        String targetNoteId = targetArchive == null ? null : archiveService.noteId(targetArchive.getId());
        String targetLinkPath = targetArchive == null ? null : archiveService.relativeNotePath(targetArchive);
        String targetLabel = targetArchive == null ? null : archiveService.displayTitle(targetArchive);
        MarkdownDocument document = mapper.toMarkdown(patch, targetNoteId, targetLinkPath, targetLabel);
        repository.save(targetPath, document);

        if (currentPath != null && !currentPath.equals(targetPath)) {
            repository.delete(currentPath);
            log.info("[Patch Markdown] patch 文件已迁移，patchId={}, oldPath={}, newPath={}",
                    patch.getId(), currentPath, targetPath);
        }

        locations.put(patch.getId(), targetPath);
        return patch;
    }

    public List<MemoryPatchDocument> saveAll(List<MemoryPatchDocument> patches) {
        List<MemoryPatchDocument> saved = new ArrayList<>();
        for (MemoryPatchDocument patch : patches) {
            saved.add(save(patch));
        }
        return saved;
    }

    public Optional<MemoryPatchDocument> findById(Long id) {
        ensureIndexLoaded();
        Path path = locations.get(id);
        if (path == null) {
            return Optional.empty();
        }
        return repository.find(path).map(mapper::fromMarkdown);
    }

    public List<MemoryPatchDocument> findByTargetMemoryIdAndCompressedFalseOrderByCreatedAtAsc(Long targetMemoryId) {
        ensureIndexLoaded();
        return locations.keySet().stream()
                .map(this::findById)
                .flatMap(Optional::stream)
                .filter(patch -> targetMemoryId != null && targetMemoryId.equals(patch.getTargetMemoryId()))
                .filter(patch -> !Boolean.TRUE.equals(patch.getCompressed()))
                .sorted(java.util.Comparator.comparing(MemoryPatchDocument::getCreatedAt))
                .toList();
    }

    public long countByTargetMemoryIdAndCompressedFalse(Long targetMemoryId) {
        return findByTargetMemoryIdAndCompressedFalseOrderByCreatedAtAsc(targetMemoryId).size();
    }

    public long countByCompressedFalse() {
        ensureIndexLoaded();
        return locations.keySet().stream()
                .map(this::findById)
                .flatMap(Optional::stream)
                .filter(patch -> !Boolean.TRUE.equals(patch.getCompressed()))
                .count();
    }

    public List<Long> findDistinctTargetMemoryIdsByCompressedFalse() {
        ensureIndexLoaded();
        return locations.keySet().stream()
                .map(this::findById)
                .flatMap(Optional::stream)
                .filter(patch -> !Boolean.TRUE.equals(patch.getCompressed()))
                .map(MemoryPatchDocument::getTargetMemoryId)
                .filter(id -> id != null)
                .distinct()
                .sorted()
                .toList();
    }

    private void ensureIndexLoaded() {
        if (indexLoaded.get()) {
            return;
        }

        synchronized (indexLock) {
            if (indexLoaded.get()) {
                return;
            }

            Map<Long, Path> loadedLocations = new LinkedHashMap<>();
            long maxId = 0;
            for (Path path : repository.listAllPaths()) {
                MarkdownDocument document = repository.find(path).orElse(null);
                if (document == null) {
                    continue;
                }

                MemoryPatchDocument patch = mapper.fromMarkdown(document);
                if (patch.getId() == null) {
                    continue;
                }

                loadedLocations.put(patch.getId(), path);
                maxId = Math.max(maxId, patch.getId());
            }

            locations.clear();
            locations.putAll(loadedLocations);
            sequence.set(maxId);
            indexLoaded.set(true);
        }
    }

    private Path resolveTargetPath(String sessionId, MemoryPatchDocument patch) {
        String noteId = mapper.noteId(patch.getId());
        String root = props.getMdRepository().getPath();
        if (!Boolean.TRUE.equals(patch.getCompressed())) {
            return Paths.get(root, "sessions", sessionId, "patches", "open", noteId + ".md");
        }

        LocalDate bucketDate = resolveArchiveDate(patch);
        String year = String.format("%04d", bucketDate.getYear());
        String yearMonth = String.format("%04d-%02d", bucketDate.getYear(), bucketDate.getMonthValue());
        return Paths.get(root, "sessions", sessionId, "patches", "archive", year, yearMonth, noteId + ".md");
    }

    private LocalDate resolveArchiveDate(MemoryPatchDocument patch) {
        LocalDateTime dateTime = patch.getCompressedAt() != null ? patch.getCompressedAt() : patch.getCreatedAt();
        return (dateTime == null ? LocalDateTime.now() : dateTime).toLocalDate();
    }
}
