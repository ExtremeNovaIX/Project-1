package p1.benchmark.halumem;

import java.util.List;

public record HaluMemSessionIngestRequest(String sessionId,
                                          String sessionLabel,
                                          List<String> sourceRefs,
                                          List<HaluMemMessageDTO> dialogue) {
}
