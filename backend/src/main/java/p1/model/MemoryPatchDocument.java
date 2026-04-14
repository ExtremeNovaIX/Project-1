package p1.model;

import jakarta.persistence.PrePersist;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class MemoryPatchDocument {

    private Long id;

    private Long targetMemoryId;

    private String correctionContent;

    private LocalDateTime createdAt = LocalDateTime.now();

    private Boolean compressed = false;

    private LocalDateTime compressedAt;
}
