package p1.model;

import p1.model.dto.ExtractedMemoryEventDTO;

import java.util.List;

public record FactExtractionPipelineResult(List<ExtractedMemoryEventDTO> events,
                                           List<String> tags,
                                           String summary) {
}
