package p1.model.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import p1.component.agent.model.ArchiveLinkRecord;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
/**
 * 长期事件层的权威文档对象，每条 archive 都代表一个落盘后的原子事件节点。
 * 它既会被写入 markdown，也会被写入向量库，并作为事件图中的基础节点。
 */
public class MemoryArchiveDocument {

    private Long id;

    private String sessionId;

    /**
     * 当前事件所属的提取组 ID。
     * 同一轮提取里归属同一个 list 的事件会共享这个值。
     */
    private String groupId;

    /**
     * 当前事件在所属组中的顺序。
     * 第一个节点就是头节点；后续会基于这个顺序建立组内天然双向链。
     */
    private Integer groupOrder;

    /**
     * 当前事件所属组的高精度组标签。
     * 这些标签由组级摘要阶段产出，并会展开到 archive 文档的 frontmatter.tags 中。
     */
    private List<String> groupTags = new ArrayList<>();

    /**
     * 当前 archive 对应事件的主题。
     * 它既是 markdown 标题和文件名的来源，也是事件级语义的权威字段。
     */
    private String topic;

    private String keywordSummary;

    private String narrative;

    /**
     * 事件图正文块，仅保存组内相邻节点的 markdown wikilink，不进入 frontmatter。
     */
    private String eventGraph;

    /**
     * 事件图构造时记录的 recent-window 命中与聚合过程。
     * 这是调试/排查字段，只写入 markdown 正文，不进入 frontmatter。
     */
    private String eventGraphTrace;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 原始来源引用。
     * 目前主要为将来 raw/source provenance 预留，当前系统还没有系统性回填。
     */
    private List<String> sourceRefs = new ArrayList<>();

    /**
     * 当前 archive 节点发出的边列表。
     * 这是粗糙图的一部分权威存储，包含组内天然边和跨组命中边。
     */
    private List<ArchiveLinkRecord> links = new ArrayList<>();
}
