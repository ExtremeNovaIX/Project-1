package p1.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.service.AiServices;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import p1.component.ai.assistant.FrontendAssistant;
import p1.component.ai.assistant.TestAssistant;
import p1.component.ai.memory.ArchivableChatMemory;
import p1.component.ai.memory.ChatMessageAppender;
import p1.component.ai.memory.MemoryCompressor;
import p1.component.ai.service.FactExtractionAiService;
import p1.component.ai.service.MemoryLogicJudgeAiService;
import p1.component.ai.service.MemoryPatchMergeAiService;
import p1.component.ai.tools.MemorySearchTools;
import p1.component.ai.vector.LuceneMemoryVectorStore;
import p1.component.ai.vector.MemoryVectorStore;
import p1.component.log.AiServiceLoggingListener;
import p1.component.log.AssistantLoggingListener;
import p1.config.prop.AssistantProperties;
import org.springframework.util.StringUtils;
import p1.repo.db.ChatLogRepository;
import p1.service.markdown.DialogueMarkdownService;
import p1.utils.SessionUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@AllArgsConstructor
@Slf4j
public class AiConfig {

    private final AssistantProperties props;
    private final Map<String, ArchivableChatMemory> memoryCache = new ConcurrentHashMap<>();
    private final AiServiceLoggingListener aiServiceLoggingListener;
    private final AssistantLoggingListener assistantLoggingListener;

    @Bean
    public ChatModel chatLanguageModel() {
        AssistantProperties.ChatModelConfig chatModelConfig = props.activeChatModel();
        return buildChatModel(chatModelConfig, assistantLoggingListener, null);
    }

    @Bean(name = "localChatModel")
    public ChatModel localChatModel() {
        AssistantProperties.ChatModelConfig chatModelConfig = props.activeChatModel();
        return buildChatModel(chatModelConfig, assistantLoggingListener, 0.8);
    }

    @Bean(name = "backendChatModel")
    public ChatModel backendChatModel() {
        AssistantProperties.ChatModelConfig config = props.activeChatModel();
        return buildChatModel(config, aiServiceLoggingListener, 0.0);
    }

    @Bean(name = "testChatModel")
    public ChatModel testChatModel() {
        AssistantProperties.ChatModelConfig chatModelConfig = props.activeChatModel();
        return buildChatModel(chatModelConfig, null, null);
    }

    @Bean
    public FrontendAssistant frontendAssistant(@Qualifier("localChatModel") ChatModel chatModel,
                                               ChatMemoryProvider chatMemoryProvider,
                                               MemorySearchTools memorySearchTools) {
        return AiServices.builder(FrontendAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(memorySearchTools)
                .build();
    }

    @Bean
    public TestAssistant testAssistant(@Qualifier("localChatModel") ChatModel chatModel) {
        return AiServices.builder(TestAssistant.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    public FactExtractionAiService factExtractionAiService(@Qualifier("localChatModel") ChatModel backendChatModel) {
        return AiServices.builder(FactExtractionAiService.class)
                .chatModel(backendChatModel)
                .build();
    }

    @Bean
    public MemoryLogicJudgeAiService memoryLogicJudgeAiService(@Qualifier("localChatModel") ChatModel backendChatModel) {
        return AiServices.builder(MemoryLogicJudgeAiService.class)
                .chatModel(backendChatModel)
                .build();
    }

    @Bean
    public MemoryPatchMergeAiService memoryPatchMergeAiService(@Qualifier("localChatModel") ChatModel backendChatModel) {
        return AiServices.builder(MemoryPatchMergeAiService.class)
                .chatModel(backendChatModel)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(MemoryCompressor compressor,
                                                 ChatMessageAppender dbAppender,
                                                 DialogueMarkdownService dialogueMarkdownService,
                                                 ChatLogRepository chatLogRepository) {
        return memoryId -> {
            String sessionId = SessionUtil.normalizeSessionId(memoryId.toString());
            return memoryCache.computeIfAbsent(sessionId,
                    id -> new ArchivableChatMemory(id, compressor, dbAppender, dialogueMarkdownService, props, chatLogRepository)
            );
        };
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        AssistantProperties.EmbeddingModelConfig embeddingModelConfig = props.activeEmbeddingModel();
        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .baseUrl(embeddingModelConfig.getBaseUrl())
                .modelName(embeddingModelConfig.getModelName());

        if (StringUtils.hasText(embeddingModelConfig.getApiKey())) {
            builder.apiKey(embeddingModelConfig.getApiKey());
        }

        if (isLocalOpenAiCompatibleEndpoint(embeddingModelConfig.getBaseUrl())) {
            builder.httpClientBuilder(localHttpClientBuilder(Duration.ofSeconds(30)));
        }

        return builder.build();
    }

    @Bean
    public MemoryVectorStore memoryVectorStore() {
        return new LuceneMemoryVectorStore(Paths.get(props.getEmbeddingStore().getPath()));
    }

    @Bean
    public ApplicationRunner aiModeStartupLogger() {
        return args -> {
            AssistantProperties.ChatModelConfig chatModel = props.activeChatModel();
            AssistantProperties.EmbeddingModelConfig embeddingModel = props.activeEmbeddingModel();
            log.info("AI mode: {} | chat-model: {} @ {} | embedding-model: {} @ {}",
                    props.getMode(),
                    chatModel.getModelName(),
                    chatModel.getBaseUrl(),
                    embeddingModel.getModelName(),
                    embeddingModel.getBaseUrl());
        };
    }

    private ChatModel buildChatModel(AssistantProperties.ChatModelConfig config,
                                     ChatModelListener listener,
                                     Double temperature) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .logRequests(config.isLogEnabled())
                .logResponses(config.isLogEnabled());

        if (StringUtils.hasText(config.getApiKey())) {
            builder.apiKey(config.getApiKey());
        }

        if (isLocalOpenAiCompatibleEndpoint(config.getBaseUrl())) {
            builder.httpClientBuilder(localHttpClientBuilder(Duration.ofSeconds(config.getTimeoutSeconds())));
        }

        if (listener != null) {
            builder.listeners(Collections.singletonList(listener));
        }
        if (temperature != null) {
            builder.temperature(temperature);
        }
        return builder.build();
    }

    private boolean isLocalOpenAiCompatibleEndpoint(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return false;
        }
        try {
            String host = URI.create(baseUrl).getHost();
            return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private JdkHttpClientBuilder localHttpClientBuilder(Duration readTimeout) {
        return new JdkHttpClientBuilder()
                .httpClientBuilder(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1))
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(readTimeout);
    }
}
