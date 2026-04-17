package p1.service.markdown;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import p1.config.prop.AssistantProperties;
import p1.model.document.MemoryArchiveDocument;
import p1.repo.markdown.MemoryArchiveMarkdownRepository;
import p1.repo.markdown.model.MarkdownDocument;
import p1.service.markdown.assembler.MemoryArchiveMdAssembler;
import p1.utils.SessionUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryArchiveMarkdownService {

    private final AssistantProperties props;
    private final MemoryArchiveMarkdownRepository repository;
    private final MemoryArchiveMdAssembler mapper;

    /**
     * 内存索引：记录 archiveId 对应的实际文件位置。
     * 这样查单个 archive 时就不需要每次全盘扫描。
     */
    private final ConcurrentHashMap<Long, ArchiveLocation> locations = new ConcurrentHashMap<>();
    private final Object indexLock = new Object();
    private final AtomicBoolean indexLoaded = new AtomicBoolean(false);
    private final AtomicLong sequence = new AtomicLong(0);

    /**
     * 判断某个 archive 是否存在。
     * 这里依赖内存索引，而不是直接查文件系统。
     */
    public boolean existsById(Long id) {
        ensureIndexLoaded();
        return id != null && locations.containsKey(id);
    }

    /**
     * 按 archiveId 读取单个 archive。
     * 如果索引里找不到位置，就直接返回空。
     */
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

    /**
     * 读取全部 archive，并按 id 升序返回。
     * 这主要用于全量索引构建、调试或管理场景。
     */
    public List<MemoryArchiveDocument> findAllOrderByIdAsc() {
        ensureIndexLoaded();
        return locations.keySet().stream()
                .sorted()
                .map(this::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * 只读取指定 session 下的 archive，并按 id 升序返回。
     * 这里直接利用内存索引里的 session 信息过滤，避免先取全量再二次筛选。
     */
    public List<MemoryArchiveDocument> findAllOrderByIdAsc(String sessionId) {
        ensureIndexLoaded();
        String normalizedSessionId = SessionUtil.normalizeSessionId(sessionId);
        return locations.entrySet().stream()
                .filter(entry -> normalizedSessionId.equals(entry.getValue().sessionId()))
                .map(Map.Entry::getKey)
                .sorted()
                .map(this::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * 通过显式字段新建一个 archive。
     * 这是一个轻量便捷入口，最终仍然走统一的 `save(...)`。
     */
    public MemoryArchiveDocument create(String sessionId, String topic, String keywordSummary, String detailedSummary) {
        MemoryArchiveDocument archive = new MemoryArchiveDocument();
        archive.setSessionId(sessionId);
        archive.setTopic(topic);
        archive.setKeywordSummary(keywordSummary);
        archive.setNarrative(detailedSummary);
        return save(archive);
    }

    /**
     * 保存 archive 到 markdown。
     * 这里统一负责：
     * 1. 分配新 ID。
     * 2. 规范化字段。
     * 3. 选择最终文件路径。
     * 4. 更新内存索引。
     */
    public MemoryArchiveDocument save(MemoryArchiveDocument archive) {
        ensureIndexLoaded();

        boolean isNew = archive.getId() == null;
        LocalDateTime now = LocalDateTime.now();
        archive.setSessionId(SessionUtil.normalizeSessionId(archive.getSessionId()));
        archive.setGroupId(normalize(archive.getGroupId()));
        archive.setTopic(normalize(archive.getTopic()));
        archive.setKeywordSummary(normalize(archive.getKeywordSummary()));
        archive.setNarrative(normalize(archive.getNarrative()));
        archive.setEventGraph(normalize(archive.getEventGraph()));
        archive.setEventGraphTrace(normalize(archive.getEventGraphTrace()));
        archive.setGroupTags(normalizeTags(archive.getGroupTags()));

        if (isNew) {
            archive.setId(sequence.incrementAndGet());
            archive.setCreatedAt(now);
            archive.setUpdatedAt(now);
        } else {
            if (archive.getCreatedAt() == null) {
                archive.setCreatedAt(now);
            }
            archive.setUpdatedAt(now);
        }

        // 文件路径依赖标题，而标题又依赖 topic，所以更新 topic 时这里可能触发重命名。
        // 计算最终目标路径
        ArchiveLocation currentLocation = locations.get(archive.getId());
        Path targetPath = resolveTargetPath(archive, currentLocation);
        MarkdownDocument document = mapper.toMarkdown(archive, buildRenderedLinks(archive));
        repository.save(targetPath, document);

        // 处理重命名
        if (currentLocation != null && !currentLocation.path().equals(targetPath)) {
            repository.delete(currentLocation.path());
            log.info("[记忆 Markdown] archive 文件已重命名，archiveId={}, oldPath={}, newPath={}",
                    archive.getId(), currentLocation.path(), targetPath);
        }

        // 更新内存索引
        locations.put(archive.getId(), new ArchiveLocation(archive.getSessionId(), targetPath));
        return archive;
    }

    /**
     * 生成对外暴露的 note id。
     */
    public String noteId(Long id) {
        return mapper.noteId(id);
    }

    /**
     * 获取 archive 的展示标题。
     * 目前这同时也是文件名的基准来源。
     */
    public String displayTitle(MemoryArchiveDocument archive) {
        return mapper.buildTitle(archive);
    }

    /**
     * 返回 archive 的相对 note 路径。
     * 结果不带 `.md` 后缀，便于后续拼 Obsidian 链接。
     */
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

    /**
     * 懒加载 archive 索引。
     * 只有第一次读写 archive 时才会真正扫描目录。
     */
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

    /**
     * 为 archive 计算最终目标路径。
     * 由于文件名会跟着 `topic` 变化，所以更新 archive 时这里也可能触发重命名。
     */
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

    /**
     * 判断某个候选路径是否可用。
     * 这里会排除“自己占用自己当前路径”的情况。
     */
    private boolean isPathAvailable(Path candidatePath, Long archiveId) {
        String normalizedCandidate = candidatePath.normalize().toString();
        return locations.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(archiveId))
                .map(entry -> entry.getValue().path().normalize().toString())
                .noneMatch(normalizedCandidate::equals);
    }

    /**
     * 清理文件名中的非法字符，并做长度保护。
     */
    private String sanitizeFileStem(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "untitled-memory";
        normalized = normalized.replaceAll("[\\\\/:*?\"<>|#\\[\\]]", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "untitled-memory";
        }
        return normalized.length() > 80 ? normalized.substring(0, 80).trim() : normalized;
    }

    /**
     * 统一字符串空值处理。
     */
    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String tag : tags) {
            String value = normalize(tag);
            if (!value.isBlank() && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    /**
     * 把元数据里的 target_id 解析成正文可点击的 Obsidian [[path|label]] 链接。
     * 这里依赖内存索引拿到目标 note 的真实路径，保证正文里的链接和文件系统保持一致。
     */
    private List<MemoryArchiveMdAssembler.RenderedArchiveLink> buildRenderedLinks(MemoryArchiveDocument archive) {
        if (archive == null || archive.getLinks() == null || archive.getLinks().isEmpty()) {
            return List.of();
        }

        Path root = Paths.get(props.getMdRepository().getPath()).toAbsolutePath().normalize();
        List<MemoryArchiveMdAssembler.RenderedArchiveLink> renderedLinks = new ArrayList<>();
        archive.getLinks().forEach(link -> {
            if (link == null || link.getTargetArchiveId() == null) {
                return;
            }

            // 正文里的 Obsidian 链接只是展示层映射，权威关系仍然保存在 frontmatter.links。
            ArchiveLocation targetLocation = locations.get(link.getTargetArchiveId());
            String targetPath = targetLocation == null
                    ? mapper.noteId(link.getTargetArchiveId())
                    : toRelativeNotePath(root, targetLocation.path());
            String targetLabel = targetLocation == null
                    ? mapper.noteId(link.getTargetArchiveId())
                    : stripMarkdownExtension(targetLocation.path().getFileName().toString());

            String wikilink = "[[" + targetPath + "|" + targetLabel + "]]";
            renderedLinks.add(new MemoryArchiveMdAssembler.RenderedArchiveLink(
                    normalize(link.getRelation()),
                    wikilink,
                    normalize(link.getReason())
            ));
        });
        return renderedLinks;
    }

    private String toRelativeNotePath(Path root, Path absolutePath) {
        return root.relativize(absolutePath.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/')
                .replaceAll("\\.md$", "");
    }

    private String stripMarkdownExtension(String filename) {
        return filename == null ? "" : filename.replaceFirst("\\.md$", "");
    }

    private record ArchiveLocation(String sessionId, Path path) {
    }
}
