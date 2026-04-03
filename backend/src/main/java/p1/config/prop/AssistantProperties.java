package p1.config.prop;

import jakarta.annotation.PostConstruct;
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
        private double duplicationThreshold;// 去重线 (与旧记忆相似度高于此值直接丢弃)
        private double relatedThreshold;// 关联线 (与旧记忆相似度介于此值与去重线之间，触发LLM审核)
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