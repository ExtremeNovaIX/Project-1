package p1.service.markdown;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import p1.config.prop.AssistantProperties;
import p1.model.MemoryArchiveDocument;
import p1.repo.markdown.MemoryArchiveMarkdownRepository;
import p1.repo.markdown.model.MarkdownDocument;
import p1.utils.SessionUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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
public class MemoryArchiveMarkdownService {

    private final AssistantProperties props;
    private final MemoryArchiveMarkdownRepository repository;
    private final MemoryArchiveMarkdownMapper mapper;

    private final ConcurrentHashMap<Long, ArchiveLocation> locations = new ConcurrentHashMap<>();
    private final Object indexLock = new Object();
    private final AtomicBoolean indexLoaded = new AtomicBoolean(false);
    private final AtomicLong sequence = new AtomicLong(0);

    public boolean existsById(Long id) {
        ensureIndexLoaded();
        return id != null && locations.containsKey(id);
    }

    public Optional<MemoryArchiveDocument> findById(Long id) {
        ensureIndexLoaded();
        if (id == null) {
            return Optional.empty();
        }

        ArchiveLocation location = locations.get(id);
        if (location == null) {
            return Optional.empty();
        }

        return repository.find(location.path())
                .map(mapper::fromMarkdown);
    }

    public List<MemoryArchiveDocument> findAllOrderByIdAsc() {
        ensureIndexLoaded();
        return locations.keySet().stream()
                .sorted()
                .map(this::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    public MemoryArchiveDocument create(String sessionId, String category, String keywordSummary, String detailedSummary) {
        MemoryArchiveDocument archive = new MemoryArchiveDocument();
        archive.setSessionId(sessionId);
        archive.setCategory(category);
        archive.setKeywordSummary(keywordSummary);
        archive.setDetailedSummary(detailedSummary);
        archive.setMergeCount(0);
        return save(archive);
    }

    public MemoryArchiveDocument save(MemoryArchiveDocument archive) {
        ensureIndexLoaded();

        boolean isNew = archive.getId() == null;
        LocalDateTime now = LocalDateTime.now();
        archive.setSessionId(SessionUtil.normalizeSessionId(archive.getSessionId()));
        archive.setCategory(normalize(archive.getCategory()));
        archive.setKeywordSummary(normalize(archive.getKeywordSummary()));
        archive.setDetailedSummary(normalize(archive.getDetailedSummary()));

        if (isNew) {
            archive.setId(sequence.incrementAndGet());
            archive.setCreatedAt(now);
            archive.setUpdatedAt(now);
            if (archive.getMergeCount() == null) {
                archive.setMergeCount(0);
            }
        } else {
            if (archive.getCreatedAt() == null) {
                archive.setCreatedAt(now);
            }
            archive.setUpdatedAt(now);
            if (archive.getMergeCount() == null) {
                archive.setMergeCount(0);
            }
        }

        ArchiveLocation currentLocation = locations.get(archive.getId());
        Path targetPath = resolveTargetPath(archive, currentLocation);
        MarkdownDocument document = mapper.toMarkdown(archive);
        repository.save(targetPath, document);

        if (currentLocation != null && !currentLocation.path().equals(targetPath)) {
            repository.delete(currentLocation.path());
            log.info("[记忆 Markdown] memory 文件已重命名，memoryId={}, oldPath={}, newPath={}",
                    archive.getId(), currentLocation.path(), targetPath);
        }

        locations.put(archive.getId(), new ArchiveLocation(archive.getSessionId(), targetPath));
        return archive;
    }

    public String noteId(Long id) {
        return mapper.noteId(id);
    }

    public String displayTitle(MemoryArchiveDocument archive) {
        return mapper.buildTitle(archive);
    }

    public String relativeNotePath(MemoryArchiveDocument archive) {
        ensureIndexLoaded();
        if (archive == null || archive.getId() == null) {
            return "";
        }

        ArchiveLocation location = locations.get(archive.getId());
        if (location == null) {
            return "";
        }

        Path root = Paths.get(props.getMdRepository().getPath()).toAbsolutePath().normalize();
        return root.relativize(location.path().toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/')
                .replaceAll("\\.md$", "");
    }

    private void ensureIndexLoaded() {
        if (indexLoaded.get()) {
            return;
        }

        synchronized (indexLock) {
            if (indexLoaded.get()) {
                return;
            }

            Map<Long, ArchiveLocation> loadedLocations = new LinkedHashMap<>();
            long maxId = 0;
            for (Path path : repository.listAllPaths()) {
                MarkdownDocument document = repository.find(path).orElse(null);
                if (document == null) {
                    continue;
                }

                MemoryArchiveDocument archive = mapper.fromMarkdown(document);
                if (archive.getId() == null) {
                    continue;
                }

                loadedLocations.put(archive.getId(), new ArchiveLocation(
                        SessionUtil.normalizeSessionId(archive.getSessionId()),
                        path
                ));
                maxId = Math.max(maxId, archive.getId());
            }

            locations.clear();
            locations.putAll(loadedLocations);
            sequence.set(maxId);
            indexLoaded.set(true);
        }
    }

    private Path resolveTargetPath(MemoryArchiveDocument archive, ArchiveLocation currentLocation) {
        String fileStem = sanitizeFileStem(mapper.buildTitle(archive));
        Path baseDir = Paths.get(props.getMdRepository().getPath(), "sessions", archive.getSessionId(), "wiki", "memories");

        int suffix = 1;
        while (true) {
            String candidateStem = suffix == 1 ? fileStem : fileStem + "-" + suffix;
            Path candidatePath = baseDir.resolve(candidateStem + ".md");
            if (currentLocation != null && currentLocation.path().equals(candidatePath)) {
                return candidatePath;
            }
            if (isPathAvailable(candidatePath, archive.getId())) {
                return candidatePath;
            }
            suffix++;
        }
    }

    private boolean isPathAvailable(Path candidatePath, Long archiveId) {
        String normalizedCandidate = candidatePath.normalize().toString();
        return locations.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(archiveId))
                .map(entry -> entry.getValue().path().normalize().toString())
                .noneMatch(normalizedCandidate::equals);
    }

    private String sanitizeFileStem(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "untitled-memory";
        normalized = normalized.replaceAll("[\\\\/:*?\"<>|#\\[\\]]", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "untitled-memory";
        }
        return normalized.length() > 80 ? normalized.substring(0, 80).trim() : normalized;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private record ArchiveLocation(String sessionId, Path path) {
    }
}
