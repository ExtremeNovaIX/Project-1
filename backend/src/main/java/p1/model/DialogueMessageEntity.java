package p1.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import p1.model.enums.DialogueMemoryStatus;
import p1.model.enums.DialogueMessageRole;

import java.time.LocalDateTime;

@Entity
@Table(name = "dialogue_messages")
@Data
@NoArgsConstructor
public class DialogueMessageEntity {
    // 只存用户和助手的原始对话，用于后续记忆压缩与补偿恢复
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DialogueMessageRole role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String textContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DialogueMemoryStatus memoryStatus;

    // 同一批进入压缩流程的对话消息共用一个批次号
    private Long batchId;

    private LocalDateTime archivedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.memoryStatus == null) {
            this.memoryStatus = DialogueMemoryStatus.PENDING;
        }
    }
}
