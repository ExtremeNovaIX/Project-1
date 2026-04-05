package p1.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "memory_patches")
@NoArgsConstructor
public class MemoryPatchEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long targetMemoryId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String correctionContent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Boolean compressed = false;

    private LocalDateTime compressedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.compressed == null) {
            this.compressed = false;
        }
    }
}
