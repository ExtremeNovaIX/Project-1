package p1.config.prop;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "assistant")
public class AssistantProperties {
    private ChatModelConfig chatModel;
    private EmbeddingModelConfig embeddingModel;
    private EmbeddingStoreConfig embeddingStore;
    private ChatMemoryConfig chatMemory;

    @Data
    public static class ChatModelConfig {
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private Long timeoutSeconds;
        private boolean logEnabled;
        private String prompt;
    }

    @Data
    public static class ChatMemoryConfig {
        private Integer maxMessages;
        private Integer compressCount;
        private Integer triggerCompressThreshold;
        private int contextMaxSummaryCount;
        private double duplicationThreshold;
        private double relatedThreshold;
        private int patchMergeThreshold;
        private int patchMergeMaxCount;
        private long patchMergeScanFixedDelayMs;
    }

    @Data
    public static class EmbeddingModelConfig {
        private String apiKey;
        private String baseUrl;
        private String modelName;
    }

    @Data
    public static class EmbeddingStoreConfig {
        private String path;
    }
}
