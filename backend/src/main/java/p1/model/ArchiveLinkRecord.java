package p1.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import p1.model.document.MemoryArchiveDocument;

/**
 * archive 节点之间的一条边。
 * 这是内嵌在 {@link MemoryArchiveDocument} 里的轻量关系记录，
 * 用来表达时间顺序边和 recent-24h 命中后形成的粗糙图连接。
 */
@Data
@NoArgsConstructor
public class ArchiveLinkRecord {

    /**
     * 边的关系类型。
     * 例如：next_in_time、previous_in_time、recent_window_to、recent_window_from。
     */
    private String relation;

    /**
     * 目标 archive 的稳定主键。
     * 这里始终指向长期 archive，而不是 recent-24h 向量文档。
     */
    private Long targetArchiveId;

    /**
     * 目标 archive 的主题。
     * 这是写边时捕获的目标节点标题快照，主要给 markdown/frontmatter 调试和人工排查使用。
     */
    private String targetTopic;

    /**
     * 这条边的置信度。
     * 时间顺序边通常固定为 1.0，recent-24h 命中边则记录相似度分数。
     */
    private Double confidence;

    /**
     * 这条边是如何产生的简要说明。
     * 主要给后续系统和人工排查使用，避免只剩一个抽象 relation。
     */
    private String reason;

    public ArchiveLinkRecord(String relation,
                             Long targetArchiveId,
                             Double confidence,
                             String reason) {
        this(relation, targetArchiveId, null, confidence, reason);
    }

    public ArchiveLinkRecord(String relation,
                             Long targetArchiveId,
                             String targetTopic,
                             Double confidence,
                             String reason) {
        this.relation = relation;
        this.targetArchiveId = targetArchiveId;
        this.targetTopic = targetTopic;
        this.confidence = confidence;
        this.reason = reason;
    }
}
