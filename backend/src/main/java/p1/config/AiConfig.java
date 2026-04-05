package p1.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.service.AiServices;
import lombok.AllArgsConstructor;
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

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@AllArgsConstructor
public class AiConfig {

    private final AssistantProperties props;
    private final Map<String, ArchivableChatMemory> memoryCache = new ConcurrentHashMap<>();
    private final AiServiceLoggingListener aiServiceLoggingListener;
    private final AssistantLoggingListener assistantLoggingListener;

    @Bean
    public ChatModel chatLanguageModel() {
        AssistantProperties.ChatModelConfig chatModelConfig = props.getChatModel();
        return OpenAiChatModel.builder()
                .apiKey(chatModelConfig.getApiKey())
                .baseUrl(chatModelConfig.getBaseUrl())
                .modelName(chatModelConfig.getModelName())
                .timeout(Duration.ofSeconds(chatModelConfig.getTimeoutSeconds()))
                .logRequests(chatModelConfig.isLogEnabled())
                .logResponses(chatModelConfig.isLogEnabled())
                .listeners(Collections.singletonList(assistantLoggingListener))
                .build();
    }

    @Bean(name = "backendChatModel")
    public ChatModel backendChatModel() {
        AssistantProperties.ChatModelConfig config = props.getChatModel();
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .logRequests(config.isLogEnabled())
                .logResponses(config.isLogEnabled())
                .listeners(Collections.singletonList(aiServiceLoggingListener))
                .temperature(0.0)
                .build();
    }

    @Bean(name = "testChatModel")
    public ChatModel testChatModel() {
        AssistantProperties.ChatModelConfig chatModelConfig = props.getChatModel();
        return OpenAiChatModel.builder()
                .apiKey(chatModelConfig.getApiKey())
                .baseUrl(chatModelConfig.getBaseUrl())
                .modelName(chatModelConfig.getModelName())
                .timeout(Duration.ofSeconds(chatModelConfig.getTimeoutSeconds()))
                .logRequests(chatModelConfig.isLogEnabled())
                .logResponses(chatModelConfig.isLogEnabled())
                .build();
    }

    @Bean
    public FrontendAssistant frontendAssistant(@Qualifier("chatLanguageModel") ChatModel chatModel,
                                               ChatMemoryProvider chatMemoryProvider,
                                               MemorySearchTools memorySearchTools) {
        return AiServices.builder(FrontendAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(memorySearchTools)
                .build();
    }

    @Bean
    public TestAssistant testAssistant(@Qualifier("testChatModel") ChatModel chatModel) {
        return AiServices.builder(TestAssistant.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    public FactExtractionAiService factExtractionAiService(@Qualifier("backendChatModel") ChatModel backendChatModel) {
        return AiServices.builder(FactExtractionAiService.class)
                .chatModel(backendChatModel)
                .build();
    }

    @Bean
    public MemoryLogicJudgeAiService memoryLogicJudgeAiService(@Qualifier("backendChatModel") ChatModel backendChatModel) {
        return AiServices.builder(MemoryLogicJudgeAiService.class)
                .chatModel(backendChatModel)
                .build();
    }

    @Bean
    public MemoryPatchMergeAiService memoryPatchMergeAiService(@Qualifier("backendChatModel") ChatModel backendChatModel) {
        return AiServices.builder(MemoryPatchMergeAiService.class)
                .chatModel(backendChatModel)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(MemoryCompressor compressor,
                                                 ChatMessageAppender dbAppender) {
        return memoryId -> {
            String sessionId = memoryId.toString();
            return memoryCache.computeIfAbsent(sessionId,
                    id -> new ArchivableChatMemory(id, compressor, dbAppender, props)
            );
        };
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        AssistantProperties.EmbeddingModelConfig embeddingModelConfig = props.getEmbeddingModel();
        return OpenAiEmbeddingModel.builder()
                .baseUrl(embeddingModelConfig.getBaseUrl())
                .apiKey(embeddingModelConfig.getApiKey())
                .modelName(embeddingModelConfig.getModelName())
                .build();
    }

    @Bean
    public MemoryVectorStore memoryVectorStore() {
        return new LuceneMemoryVectorStore(Paths.get(props.getEmbeddingStore().getPath()));
    }
}
