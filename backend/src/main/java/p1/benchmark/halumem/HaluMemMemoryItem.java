package p1.benchmark.halumem;

import java.util.List;

public record HaluMemMemoryItem(Long archiveId,
                                String topic,
                                String keywordSummary,
                                String narrative,
                                List<String> sourceRefs) {
}
