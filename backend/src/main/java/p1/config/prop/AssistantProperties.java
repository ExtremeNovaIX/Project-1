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
    private MdRepositoryConfig mdRepository;
    private ChatMemoryConfig chatMemory;
    private EventTreeConfig eventTree = new EventTreeConfig();
    private TestAiConfig testAi = new TestAiConfig();
    private TestChatConfig testChat = new TestChatConfig();

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
        private Integer compressCount;
        private Integer triggerCompressThreshold;
        private int contextMaxSummaryCount;
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

    @Data
    public static class MdRepositoryConfig {
        private String path = "data/memory";
    }

    @Data
    public static class EventTreeConfig {
        // recent-24h 候选的基础向量分至少达到这个阈值，才允许被拿来连边。
        private double recentWindowScoreLinkThreshold = 0.7;
        // recent 事件组窗口的保留时长，超出这个小时数的组会被 recent-window 维护逻辑淘汰。
        private int recentWindowHours = 24;
        // recent-window 过期扫描任务的固定执行间隔。
        private long recentWindowScanFixedDelayMs = 3600000;
        // 共享 tag 的 IDF boost 权重。最终候选分 = 向量基础分 * (1 + weight * idf01)。
        // 调大后，稀有共享 tag 对 recent-window rerank 的影响会更强。
        private double recentWindowIdfBoostWeight = 0.35;
        // IDF 公式里 N 侧的平滑项，用来避免 recent 组数量较少时波动过大。
        // 原始公式中的分子为 N + docCountSmoothing。
        private double recentWindowIdfDocCountSmoothing = 1.0;
        // IDF 公式里 df(tag) 侧的平滑项，用来避免 tag 只出现极少次数时权重异常放大。
        // 原始公式中的分母为 df(tag) + dfSmoothing。
        private double recentWindowIdfDfSmoothing = 1.0;
        // 将原始 IDF 累积分数压到 [0,1] 的归一化尺度。
        // 调小会更快饱和，调大会让 boost 增长更平缓。
        private double recentWindowIdfNormalizationScale = 2.0;
        // 至少共享多少个 tag 才触发 IDF boost；低于这个数量时完全不加成。
        private int recentWindowIdfMinSharedTags = 1;
        // recent-window 候选的时间衰减系数。最终时间因子 = exp(-coefficient * ageHours / recentWindowHours)。
        // 0 表示关闭时间衰减；调大后，越接近窗口尾部的旧组越难在 recent-window 竞争中胜出。
        private double recentWindowTimeDecayCoefficient = 0.5;
        // 最终连接边的最低得分阈值，低于这个值的边会被拦截。
        private double recentWindowFinalThreshold = 0.7;
    }

    @Data
    public static class TestChatConfig {
        private String selfSummaryPath = "data/test-chat";
    }

    @Data
    public static class TestAiConfig {
        private ChatModelConfig chatModel = new ChatModelConfig();
    }
}
