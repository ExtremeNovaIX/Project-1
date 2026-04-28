package p1.model.dto;

import java.util.List;

public record StoryReplayResultDTO(String sessionId,
                                   String sourcePath,
                                   Integer targetLength,
                                   int effectiveSourceLength,
                                   int chunkCount,
                                   int userMessageCount,
                                   int assistantMessageCount,
                                   int minChunkLength,
                                   int maxChunkLength,
                                   int averageChunkLength,
                                   List<String> previewChunks) {
}
