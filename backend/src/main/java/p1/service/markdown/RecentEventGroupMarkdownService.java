package p1.service.markdown;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p1.config.prop.AssistantProperties;
import p1.model.RecentEventGroupLinkRecord;
import p1.model.document.MemoryArchiveDocument;
import p1.model.document.RecentEventGroupDocument;
import p1.repo.markdown.RecentEventGroupMarkdownRepository;
import p1.repo.markdown.model.MarkdownDocument;
import p1.service.markdown.assembler.RecentEventGroupMdAssembler;
import p1.utils.SessionUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecentEventGroupMarkdownService {

    private static final DateTimeFormatter GROUP_ID_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AssistantProperties props;
    private final RecentEventGroupMarkdownRepository repository;
    private final RecentEventGroupMdAssembler mapper;
    private final MemoryArchiveMarkdownService archiveService;

    public RecentEventGroupDocument create(String groupId,
                                           String sessionId,
                                           List<MemoryArchiveDocument> archives,
                                           List<String> recentVectorDocumentIds) {
        return create(groupId, sessionId, archives, recentVectorDocumentIds, List.of());
    }

    public RecentEventGroupDocument create(String groupId,
                                           String sessionId,
                                           List<MemoryArchiveDocument> archives,
                                           List<String> recentVectorDocumentIds,
                                           List<String> groupTags) {
        String normalizedSessionId = SessionUtil.normalizeSessionId(sessionId);
        LocalDateTime now = LocalDateTime.now();

        RecentEventGroupDocument group = new RecentEventGroupDocument();
        group.setId(groupId);
        group.setSessionId(normalizedSessionId);
        group.setTopic(resolveGroupTopic(archives));
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        group.setLastHitAt(now);
        group.setHeadArchiveId(archives == null || archives.isEmpty() ? null : archives.getFirst().getId());
        group.setTailArchiveId(archives == null || archives.isEmpty() ? null : archives.getLast().getId());
        group.setArchiveIds(archives == null ? List.of() : archives.stream().map(MemoryArchiveDocument::getId).toList());
        group.setRecentVectorDocumentIds(recentVectorDocumentIds == null ? List.of() : List.copyOf(recentVectorDocumentIds));
        group.setGroupTags(groupTags == null ? List.of() : List.copyOf(groupTags));

        save(group);
        return group;
    }

    public String allocateGroupId() {
        return nextGroupId();
    }

    public RecentEventGroupDocument save(RecentEventGroupDocument group) {
        LocalDateTime now = LocalDateTime.now();
        if (group.getCreatedAt() == null) {
            group.setCreatedAt(now);
        }
        if (group.getLastHitAt() == null) {
            group.setLastHitAt(now);
        }
        group.setUpdatedAt(now);
        group.setSessionId(SessionUtil.normalizeSessionId(group.getSessionId()));
        group.setGroupTags(normalizeTags(group.getGroupTags()));
        group.setLinks(normalizeLinks(group.getLinks()));

        MarkdownDocument document = mapper.toMarkdown(group, buildRenderedArchiveRefs(group));
        repository.save(resolvePath(group.getSessionId(), group.getId()), document);
        return group;
    }

    public Optional<RecentEventGroupDocument> findById(String sessionId, String groupId) {
        String normalizedSessionId = SessionUtil.normalizeSessionId(sessionId);
        return repository.find(resolvePath(normalizedSessionId, groupId)).map(mapper::fromMarkdown);
    }

    public List<RecentEventGroupDocument> findAllBySessionId(String sessionId) {
        Path root = baseDirectory(SessionUtil.normalizeSessionId(sessionId));
        return repository.listAllPaths(root).stream()
                .map(repository::find)
                .flatMap(Optional::stream)
                .map(mapper::fromMarkdown)
                .toList();
    }

    public Optional<RecentEventGroupDocument> findLatestBySessionId(String sessionId) {
        return findAllBySessionId(sessionId).stream()
                .filter(group -> group.getCreatedAt() != null)
                .max((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()));
    }

    public List<RecentEventGroupDocument> findAll() {
        Path root = Paths.get(props.getMdRepository().getPath(), "_system", "sessions");
        return repository.listAllPaths(root).stream()
                .filter(path -> path.toString().replace('\\', '/').contains("/recent-event-groups/"))
                .map(repository::find)
                .flatMap(Optional::stream)
                .map(mapper::fromMarkdown)
                .toList();
    }

    public void touch(String sessionId, String groupId, LocalDateTime hitTime) {
        RecentEventGroupDocument group = findById(sessionId, groupId).orElse(null);
        if (group == null) {
            log.warn("[recent-event-group] touch skipped, groupId={} not found, sessionId={}", groupId, sessionId);
            return;
        }

        group.setLastHitAt(hitTime == null ? LocalDateTime.now() : hitTime);
        save(group);
    }

    /**
     * 添加组级双向链接。
     */
    public void addGroupLink(String sessionId,
                             String leftGroupId,
                             String rightGroupId,
                             String leftToRightRelation,
                             String rightToLeftRelation,
                             Double confidence,
                             String reason) {
        String normalizedSessionId = SessionUtil.normalizeSessionId(sessionId);
        String normalizedLeftGroupId = normalize(leftGroupId);
        String normalizedRightGroupId = normalize(rightGroupId);
        if (normalizedLeftGroupId.isBlank()
                || normalizedRightGroupId.isBlank()
                || normalizedLeftGroupId.equals(normalizedRightGroupId)) {
            return;
        }

        RecentEventGroupDocument leftGroup = findById(normalizedSessionId, normalizedLeftGroupId).orElse(null);
        RecentEventGroupDocument rightGroup = findById(normalizedSessionId, normalizedRightGroupId).orElse(null);
        if (leftGroup == null || rightGroup == null) {
            log.warn("[recent-event-group] skipped bidirectional link, group missing, sessionId={}, leftGroupId={}, rightGroupId={}",
                    normalizedSessionId, normalizedLeftGroupId, normalizedRightGroupId);
            return;
        }

        boolean leftChanged = mergeLink(
                leftGroup,
                normalizedRightGroupId,
                rightGroup.getTopic(),
                leftToRightRelation,
                confidence,
                reason
        );
        boolean rightChanged = mergeLink(
                rightGroup,
                normalizedLeftGroupId,
                leftGroup.getTopic(),
                rightToLeftRelation,
                confidence,
                reason
        );
        if (leftChanged) {
            save(leftGroup);
        }
        if (rightChanged) {
            save(rightGroup);
        }
    }

    public void delete(String sessionId, String groupId) {
        repository.delete(resolvePath(SessionUtil.normalizeSessionId(sessionId), groupId));
    }

    private Path resolvePath(String sessionId, String groupId) {
        return baseDirectory(sessionId).resolve(groupId + ".md");
    }

    private Path baseDirectory(String sessionId) {
        return Paths.get(props.getMdRepository().getPath(),
                "_system",
                "sessions",
                sessionId,
                "recent-event-groups");
    }

    private String nextGroupId() {
        return "group-" + LocalDateTime.now().format(GROUP_ID_TIME_FORMAT) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String resolveGroupTopic(List<MemoryArchiveDocument> archives) {
        if (archives == null || archives.isEmpty()) {
            return "";
        }
        MemoryArchiveDocument headArchive = archives.getFirst();
        return normalize(headArchive == null ? null : headArchive.getTopic());
    }

    private boolean mergeLink(RecentEventGroupDocument sourceGroup,
                              String targetGroupId,
                              String targetTopic,
                              String relation,
                              Double confidence,
                              String reason) {
        if (sourceGroup.getLinks() == null) {
            sourceGroup.setLinks(new ArrayList<>());
        }

        String normalizedTargetGroupId = normalize(targetGroupId);
        String normalizedTargetTopic = normalize(targetTopic);
        String normalizedRelation = normalize(relation);
        String normalizedReason = normalize(reason);
        for (RecentEventGroupLinkRecord existingLink : sourceGroup.getLinks()) {
            if (existingLink == null) {
                continue;
            }
            if (normalizedTargetGroupId.equals(normalize(existingLink.getTargetGroupId()))
                    && normalizedRelation.equals(normalize(existingLink.getRelation()))) {
                boolean changed = !equalsNullable(existingLink.getConfidence(), confidence)
                        || !normalize(existingLink.getTargetTopic()).equals(normalizedTargetTopic)
                        || !normalize(existingLink.getReason()).equals(normalizedReason);
                if (!changed) {
                    return false;
                }
                existingLink.setTargetTopic(normalizedTargetTopic);
                existingLink.setConfidence(confidence);
                existingLink.setReason(normalizedReason);
                return true;
            }
        }

        sourceGroup.getLinks().add(new RecentEventGroupLinkRecord(
                normalizedRelation,
                normalizedTargetGroupId,
                normalizedTargetTopic,
                confidence,
                normalizedReason
        ));
        return true;
    }

    private List<RecentEventGroupMdAssembler.RenderedArchiveRef> buildRenderedArchiveRefs(RecentEventGroupDocument group) {
        if (group == null || group.getArchiveIds() == null || group.getArchiveIds().isEmpty()) {
            return List.of();
        }

        return group.getArchiveIds().stream()
                .map(this::toRenderedArchiveRef)
                .toList();
    }

    private RecentEventGroupMdAssembler.RenderedArchiveRef toRenderedArchiveRef(Long archiveId) {
        if (archiveId == null) {
            return new RecentEventGroupMdAssembler.RenderedArchiveRef("");
        }
        return archiveService.findById(archiveId)
                .map(this::toRenderedArchiveRef)
                .orElseGet(() -> {
                    String noteId = archiveService.noteId(archiveId);
                    return new RecentEventGroupMdAssembler.RenderedArchiveRef("[[" + noteId + "|" + noteId + "]]");
                });
    }

    private RecentEventGroupMdAssembler.RenderedArchiveRef toRenderedArchiveRef(MemoryArchiveDocument archive) {
        String path = archiveService.relativeNotePath(archive);
        String label = archiveService.displayTitle(archive);
        String wikilink;
        if (path.isBlank()) {
            String noteId = archiveService.noteId(archive.getId());
            wikilink = "[[" + noteId + "|" + noteId + "]]";
        } else {
            wikilink = "[[" + path + "|" + label + "]]";
        }
        return new RecentEventGroupMdAssembler.RenderedArchiveRef(wikilink);
    }

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

    private List<RecentEventGroupLinkRecord> normalizeLinks(List<RecentEventGroupLinkRecord> links) {
        if (links == null || links.isEmpty()) {
            return new ArrayList<>();
        }

        List<RecentEventGroupLinkRecord> normalized = new ArrayList<>();
        for (RecentEventGroupLinkRecord link : links) {
            if (link == null) {
                continue;
            }
            String relation = normalize(link.getRelation());
            String targetGroupId = normalize(link.getTargetGroupId());
            if (relation.isBlank() || targetGroupId.isBlank()) {
                continue;
            }
            normalized.add(new RecentEventGroupLinkRecord(
                    relation,
                    targetGroupId,
                    normalize(link.getTargetTopic()),
                    link.getConfidence(),
                    normalize(link.getReason())
            ));
        }
        return normalized;
    }

    private boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
