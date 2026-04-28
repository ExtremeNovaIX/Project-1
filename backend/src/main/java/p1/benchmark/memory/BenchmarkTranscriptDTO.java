package p1.benchmark.memory;

import java.util.List;

public record BenchmarkTranscriptDTO(String transcriptId,
                                     String sourceSessionId,
                                     List<String> sourceRefs,
                                     List<BenchmarkMessageDTO> messages) {
}
