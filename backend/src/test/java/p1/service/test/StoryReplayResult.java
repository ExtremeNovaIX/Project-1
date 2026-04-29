package p1.service.test;

import java.util.List;

public record StoryReplayResult(String sessionId,
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
