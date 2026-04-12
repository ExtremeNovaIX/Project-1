package p1.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class MemoryArchiveDocument {

    private Long id;

    private String sessionId;

    private String category;

    private String keywordSummary;

    private String detailedSummary;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer mergeCount = 0;

    private List<Long> sourceFragmentIds = new ArrayList<>();
}
