package p1.config.prop;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "assistant")
public class AssistantProperties {
    private Mode mode = Mode.API;
    private ProviderConfig api;
    private ProviderConfig local;
    private EmbeddingStoreConfig embeddingStore;
    private ChatMemoryConfig chatMemory;

    public ChatModelConfig activeChatModel() {
        return activeProvider().getChatModel();
    }

    public EmbeddingModelConfig activeEmbeddingModel() {
        return activeProvider().getEmbeddingModel();
    }

    public ProviderConfig activeProvider() {
        return mode == Mode.LOCAL ? local : api;
    }

    public enum Mode {
        API,
        LOCAL
    }

    @Data
    public static class ProviderConfig {
        private ChatModelConfig chatModel;
        private EmbeddingModelConfig embeddingModel;
    }

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
