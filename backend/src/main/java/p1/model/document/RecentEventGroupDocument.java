package p1.model.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import p1.model.RecentEventGroupLinkRecord;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
/**
 * recent-24h 窗口里的组级元数据文档。
 * 它不是长期记忆内容本身，而是系统层的管理对象：
 * 用来记录某个事件组在 24h 向量窗口中的生命周期、命中续期和整组删除信息。
 */
public class RecentEventGroupDocument {

    private String id;

    private String sessionId;

    /**
     * 当前事件组的主题。
     * 一般沿用组内头事件 topic，便于 recent-24h 元数据调试。
     */
    private String topic;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 该组最近一次被 recent-24h 检索命中的时间。
     */
    private LocalDateTime lastHitAt;

    /**
     * 当前组头节点对应的长期 archiveId。
     * 这里是为了后续图整理、调试和可能的头节点回溯而保留的组级锚点。
     */
    private Long headArchiveId;

    /**
     * 当前组最后一个时间节点对应的长期 archiveId。
     * 新产生的事件链会确定性地接到上一批的这个尾节点上。
     */
    private Long tailArchiveId;

    /**
     * 当前组包含的全部长期 archiveId。
     */
    private List<Long> archiveIds = new ArrayList<>();

    /**
     * 当前组在 recent-24h 向量库中写入的所有向量文档 ID。
     * 保留这份列表，是为了后续可以按组快速删除 recent-24h 文档，而不需要重新搜索。
     */
    private List<String> recentVectorDocumentIds = new ArrayList<>();

    /**
     * 当前重要事件组的组级标签。
     * 它们来自第二阶段摘要，供 recent-window 的组级 IDF 打分使用。
     */
    private List<String> groupTags = new ArrayList<>();
    private List<RecentEventGroupLinkRecord> links = new ArrayList<>();
}
