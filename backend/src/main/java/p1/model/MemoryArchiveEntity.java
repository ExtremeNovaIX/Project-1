package p1.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import p1.utils.LongListConverter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "memory_archives")
@Data
@NoArgsConstructor
public class MemoryArchiveEntity {
    public static final String STATUS_FRAGMENT = "FRAGMENT";
    public static final String STATUS_CONSOLIDATED = "CONSOLIDATED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId;

    @Column(length = 50)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String keywordSummary;

    @Column(columnDefinition = "TEXT")
    private String detailedSummary;

    private LocalDateTime createdAt;

    private String status;

    @Convert(converter = LongListConverter.class)
    @Column(columnDefinition = "VARCHAR(255)")
    private List<Long> sourceFragmentIds = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.setStatus(STATUS_FRAGMENT);
    }
}