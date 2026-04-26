package p1.component.agent.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p1.infrastructure.vector.ArchiveVectorLibrary;
import p1.model.document.MemoryArchiveDocument;
import p1.model.document.RecentEventGroupDocument;
import p1.component.agent.model.ExtractedMemoryEvent;
import p1.service.archive.ArchiveEmbeddingService;
import p1.service.archive.graph.ArchiveGraphService;
import p1.service.archive.graph.ArchiveLinkService;
import p1.service.markdown.MemoryArchiveStore;
import p1.service.markdown.RecentEventGroupMarkdownService;
import p1.utils.SessionUtil;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryWriteService {

    private final MemoryArchiveStore archiveStore;
    private final RecentEventGroupMarkdownService recentEventGroupService;
    private final ArchiveLinkService archiveLinkService;
    private final ArchiveEmbeddingService archiveEmbeddingService;
    private final ArchiveGraphService archiveGraphService;

    /**
     * 按组写入事件。
     * 当前完整顺序是：
     * 1. 组内每个事件先落长期 archive库；
     * 2. 建立图结构：组内双向链、recent-24h top1 连边、命中续期；
     * 3. 最后再把当前组写入 recent-24h 向量库，并保存组元数据。
     * <p>
     * 这样做可以避免“当前组先进入 recent-24h，然后把自己命中成 top1”的问题。
     */
    public List<MemoryArchiveDocument> saveEventGroup(String sessionId, List<ExtractedMemoryEvent> events) {
        return saveEventGroup(sessionId, events, List.of());
    }

    public List<MemoryArchiveDocument> saveEventGroup(String sessionId,
                                                      List<ExtractedMemoryEvent> events,
                                                      List<String> groupTags) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        String normalizedSessionId = normalizeSessionId(sessionId);
        String groupId = recentEventGroupService.allocateGroupId();
        String groupTopic = resolveGroupTopic(events);
        List<MemoryArchiveDocument> savedArchives = new ArrayList<>();

        log.info("[事件组写入] sessionId={} 开始写入，groupId={}，groupTopic={}，eventCount={}",
                normalizedSessionId, groupId, groupTopic, events.size());

        for (int index = 0; index < events.size(); index++) {
            ExtractedMemoryEvent event = events.get(index);
            MemoryArchiveDocument archive = buildArchive(normalizedSessionId, event, groupId, index, groupTags);
            savedArchives.add(saveArchive(archive, "事件组写入"));
        }

        archiveGraphService.enrichGroupGraph(normalizedSessionId, savedArchives, groupTags);

        try {
            List<String> recentVectorDocumentIds = archiveEmbeddingService.indexArchives(
                    normalizedSessionId,
                    ArchiveVectorLibrary.RECENT_24H,
                    savedArchives,
                    groupId
            );
            RecentEventGroupDocument group = recentEventGroupService.create(
                    groupId,
                    normalizedSessionId,
                    savedArchives,
                    recentVectorDocumentIds,
                    groupTags
            );
            archiveGraphService.syncGroupLinks(normalizedSessionId, group.getId(), savedArchives);
        } catch (Exception e) {
            log.error("[事件组写入] recent-24h 写入失败，但 archive 已保留，sessionId={}，groupId={}",
                    normalizedSessionId, groupId, e);
        }

        log.info("[事件组写入] 完成，sessionId={}，groupId={}，savedArchiveCount={}",
                normalizedSessionId, groupId, savedArchives.size());
        return List.copyOf(savedArchives);
    }

    /**
     * 创建一个新的 archive，并尝试为它补充一条显式出边。
     * 这个入口主要保留给非组写入场景使用。
     */
    public MemoryArchiveDocument saveLinkedMemory(String sessionId,
                                                  ExtractedMemoryEvent event,
                                                  Long targetArchiveId,
                                                  String relation,
                                                  String reason,
                                                  Double confidence) {
        MemoryArchiveDocument archive = buildArchive(sessionId, event, null, null);
        archive = saveArchive(archive, "关联事件写入");
        return archiveLinkService.addOutgoingLink(
                archive,
                targetArchiveId,
                relation,
                confidence,
                reason
        );
    }

    /**
     * 直接按显式字段创建一个新的 archive。
     */
    public MemoryArchiveDocument saveNewMemory(String sessionId, String topic, String keywordSummary, String detailedSummary) {
        MemoryArchiveDocument archive = new MemoryArchiveDocument();
        archive.setSessionId(normalizeSessionId(sessionId));
        archive.setTopic(normalize(topic));
        archive.setKeywordSummary(normalize(keywordSummary));
        archive.setNarrative(normalize(detailedSummary));
        return saveArchive(archive, "新事件写入");
    }

    /**
     * 根据提取出的事件 DTO 创建一个新的 archive。
     */
    public MemoryArchiveDocument saveNewMemory(String sessionId, ExtractedMemoryEvent event) {
        MemoryArchiveDocument archive = buildArchive(sessionId, event, null, null);
        return saveArchive(archive, "新事件写入");
    }

    /**
     * 把提取结果映射成 archive 文档对象。
     * 这里仅负责字段归一化和组上下文注入，不负责真正落盘。
     */
    private MemoryArchiveDocument buildArchive(String sessionId,
                                               ExtractedMemoryEvent event,
                                               String groupId,
                                               Integer groupOrder) {
        return buildArchive(sessionId, event, groupId, groupOrder, List.of());
    }

    private MemoryArchiveDocument buildArchive(String sessionId,
                                               ExtractedMemoryEvent event,
                                               String groupId,
                                               Integer groupOrder,
                                               List<String> groupTags) {
        MemoryArchiveDocument archive = new MemoryArchiveDocument();
        archive.setSessionId(normalizeSessionId(sessionId));
        archive.setGroupId(normalize(groupId));
        archive.setGroupOrder(groupOrder);
        archive.setGroupTags(normalizeTags(groupTags));
        // topic 是 archive 标题、文件名和主题语义的唯一来源。
        archive.setTopic(normalize(event == null ? null : event.getTopic()));
        archive.setKeywordSummary(normalize(event == null ? null : event.getKeywordSummary()));
        archive.setNarrative(normalize(event == null ? null : event.getNarrative()));
        return archive;
    }

    /**
     * 统一执行 archive 落盘和长期 archive 向量写入。
     * 这仍然是当前长期事件写入链的唯一权威入口。
     */
    private MemoryArchiveDocument saveArchive(MemoryArchiveDocument archive, String action) {
        log.info("[Archive 写入] sessionId={} 开始，action={}，groupId={}，groupOrder={}，topic={}",
                archive.getSessionId(), action, archive.getGroupId(), archive.getGroupOrder(), archive.getTopic());
        try {
            MemoryArchiveDocument saved = archiveStore.save(archive);
            archiveEmbeddingService.indexArchives(
                    saved.getSessionId(),
                    ArchiveVectorLibrary.ARCHIVE,
                    List.of(saved),
                    saved.getGroupId()
            );
            log.info("[Archive 写入] sessionId={} 完成，action={}，archiveId={}，groupId={}，groupOrder={}，topic={}",
                    archive.getSessionId(), action, saved.getId(), saved.getGroupId(), saved.getGroupOrder(), saved.getTopic());
            return saved;
        } catch (Exception e) {
            log.error("[Archive 写入] sessionId={} 失败，action={}，groupId={}，groupOrder={}，topic={}",
                    archive.getSessionId(), action, archive.getGroupId(), archive.getGroupOrder(), archive.getTopic(), e);
            throw e;
        }
    }

    private String resolveGroupTopic(List<ExtractedMemoryEvent> events) {
        if (events == null || events.isEmpty()) {
            return "";
        }
        ExtractedMemoryEvent headEvent = events.getFirst();
        return normalize(headEvent == null ? null : headEvent.getTopic());
    }

    private String normalizeSessionId(String sessionId) {
        return SessionUtil.normalizeSessionId(sessionId);
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
}
