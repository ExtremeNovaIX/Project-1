package p1.component.agent.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import p1.model.document.RecentEventGroupDocument;

/**
 * recent event group 之间的一条边。
 * 这是内嵌在 {@link RecentEventGroupDocument} 里的轻量关系记录，
 * 用来表达组级时间顺序边和 recent-window 命中后形成的跨组连接。
 */
@Data
@NoArgsConstructor
public class RecentEventGroupLinkRecord {

    private String relation;

    private String targetGroupId;

    private String targetTopic;

    private Double confidence;

    private String reason;

    public RecentEventGroupLinkRecord(String relation,
                                      String targetGroupId,
                                      Double confidence,
                                      String reason) {
        this(relation, targetGroupId, null, confidence, reason);
    }

    public RecentEventGroupLinkRecord(String relation,
                                      String targetGroupId,
                                      String targetTopic,
                                      Double confidence,
                                      String reason) {
        this.relation = relation;
        this.targetGroupId = targetGroupId;
        this.targetTopic = targetTopic;
        this.confidence = confidence;
        this.reason = reason;
    }
}
