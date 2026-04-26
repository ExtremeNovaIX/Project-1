package p1.component.agent.model;

import java.util.List;

public record FactExtractionPipelineResult(List<ExtractedMemoryEvent> events,
                                           List<String> tags,
                                           String summary) {
}
