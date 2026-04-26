package p1.config;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.service.AiServices;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import p1.component.agent.core.RpAgent;
import p1.component.agent.context.RpRequestTimeAppender;
import p1.component.agent.aiservice.TestAiService;
import p1.component.agent.core.ToolCallingAgent;
import p1.component.agent.core.ToolCallingPlanner;
import p1.component.agent.memory.ArchivableChatMemory;
import p1.component.agent.memory.ChatMessageAppender;
import p1.component.agent.memory.MemoryAsyncCompressor;
import p1.component.agent.memory.FactEvaluatorAiService;
import p1.component.agent.memory.FactExtractionAiService;
import p1.component.agent.tools.BackendAssistantGatewayTool;
import p1.component.agent.tools.ToolCallResultStore;
import p1.component.log.AiServiceLoggingListener;
import p1.component.log.AssistantLoggingListener;
import p1.config.prop.AssistantProperties;
import p1.config.prop.LockProperties;
import p1.service.ChatLogRepository;
import p1.service.markdown.RawMdService;
import p1.utils.SessionUtil;

import java.net.URI;
import java.net.http.HttpClient;
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
        AssistantProperties.ChatModelConfig chatModelConfig = props.getTestAi().getChatModel();
        return buildChatModel(chatModelConfig, null, 1.0);
    }

    @Bean
    public RpAgent frontendAssistant(@Qualifier("localChatModel") ChatModel chatModel,
                                     ChatMemoryProvider chatMemoryProvider,
                                     RpRequestTimeAppender rpRequestTimeAppender,
                                     BackendAssistantGatewayTool backendAssistantGatewayTool) {
        return AiServices.builder(RpAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .chatRequestTransformer(rpRequestTimeAppender::augment)
                .tools(backendAssistantGatewayTool)
                .build();
    }

    @Bean
    public TestAiService testAssistant(@Qualifier("testChatModel") ChatModel chatModel) {
        return AiServices.builder(TestAiService.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    public ToolCallingPlanner toolCallingRouterPlanner(@Qualifier("backendChatModel") ChatModel backendChatModel) {
        return AiServices.builder(ToolCallingPlanner.class)
                .chatModel(backendChatModel)
                .build();
    }

    @Bean
    public ToolCallingAgent toolCallingAgent(@Qualifier("backendChatModel") ChatModel backendChatModel,
                                             ToolCallResultStore toolCallResultStore) {
        return AiServices.builder(ToolCallingAgent.class)
                .chatModel(backendChatModel)
                .afterToolExecution(toolCallResultStore::record)
                .build();
    }

    @Bean
    public FactExtractionAiService factExtractionAiService(@Qualifier("backendChatModel") ChatModel backendChatModel) {
        return AiServices.builder(FactExtractionAiService.class)
                .chatModel(backendChatModel)
                .build();
    }

    @Bean
    public FactEvaluatorAiService factScoringAiService(@Qualifier("backendChatModel") ChatModel backendChatModel) {
        return AiServices.builder(FactEvaluatorAiService.class)
                .chatModel(backendChatModel)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(MemoryAsyncCompressor compressor,
                                                 ChatMessageAppender dbAppender,
                                                 RawMdService rawMdService,
                                                 LockProperties lockProperties,
                                                 ChatLogRepository chatLogRepository) {
        return memoryId -> {
            String sessionId = SessionUtil.normalizeSessionId(memoryId.toString());
            return memoryCache.computeIfAbsent(sessionId,
                    id -> new ArchivableChatMemory(id, compressor, dbAppender, rawMdService, props, lockProperties, chatLogRepository)
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
    public ApplicationRunner aiModeStartupLogger() {
        return args -> {
            AssistantProperties.ChatModelConfig chatModel = props.activeChatModel();
            AssistantProperties.EmbeddingModelConfig embeddingModel = props.activeEmbeddingModel();
            AssistantProperties.ChatModelConfig testChatModel = props.getTestAi().getChatModel();
            log.info("AI mode: {} | chat-model: {} @ {} | embedding-model: {} @ {}",
                    props.getMode(),
                    chatModel.getModelName(),
                    chatModel.getBaseUrl(),
                    embeddingModel.getModelName(),
                    embeddingModel.getBaseUrl());
            log.info("Test AI model: {} @ {}",
                    testChatModel.getModelName(),
                    testChatModel.getBaseUrl());
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
