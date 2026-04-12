package p1.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class MemoryPatchDocument {

    private Long id;

    private Long targetMemoryId;

    private String correctionContent;

    private LocalDateTime createdAt;

    private Boolean compressed = false;

    private LocalDateTime compressedAt;
}
