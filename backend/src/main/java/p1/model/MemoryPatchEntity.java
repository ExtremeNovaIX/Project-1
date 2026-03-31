package p1.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "memory_patches")
public class MemoryPatchEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 关联主记忆的ID
    @Column(nullable = false)
    private Long targetMemoryId;

    // 补丁内容
    @Column(columnDefinition = "TEXT", nullable = false)
    private String correctionContent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
