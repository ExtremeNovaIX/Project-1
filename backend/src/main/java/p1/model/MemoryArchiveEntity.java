package p1.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "memory_archives")
@Data
public class MemoryArchiveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String keywordSummary;

    @Column(columnDefinition = "TEXT")
    private String detailedSummary;

    private LocalDateTime createdAt = LocalDateTime.now();
}