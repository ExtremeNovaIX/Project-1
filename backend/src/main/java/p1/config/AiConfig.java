package p1.config;

import dev.langchain4j.community.rag.content.retriever.lucene.LuceneEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.store.FSDirectory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import p1.service.ai.BackendAssistant;
import p1.service.ai.FrontendAssistant;
import p1.service.ai.memory.ArchivableChatMemory;
import p1.service.ai.memory.ChatMessageAppender;
import p1.service.ai.memory.MemoryCompressor;
import p1.service.ai.skills.MemorySaveTools;
import p1.service.ai.skills.MemorySearchTools;
import p1.service.ai.TestAssistant;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@RequiredArgsConstructor
public class AiConfig {

    private final AssistantProperties props;
    private final Map<String, ArchivableChatMemory> memoryCache = new ConcurrentHashMap<>();

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
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(MemoryCompressor compressor, ChatMessageAppender dbAppender) {
        return memoryId -> {
            String sessionId = memoryId.toString();
            return memoryCache.computeIfAbsent(sessionId,
                    id -> new ArchivableChatMemory(id, compressor, dbAppender)
            );
        };
    }

    @Bean
    public FrontendAssistant frontendAssistant(ChatModel chatModel, ChatMemoryProvider chatMemoryProvider, MemorySearchTools memorySearchTools) {
        return AiServices.builder(FrontendAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(memorySearchTools)
                .build();
    }

    @Bean
    public BackendAssistant backendAssistant(ChatModel chatModel, MemorySaveTools memorySaveTools) {
        return AiServices.builder(BackendAssistant.class)
                .chatModel(chatModel)
                .tools(memorySaveTools)
                .build();
    }

    @Bean
    public TestAssistant testAssistant(ChatModel chatModel) {
        return AiServices.builder(TestAssistant.class)
                .chatModel(chatModel)
                .build();
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
    public EmbeddingStore<TextSegment> embeddingStore() throws IOException {
        AssistantProperties.EmbeddingStoreConfig embeddingStoreConfig = props.getEmbeddingStore();
        return LuceneEmbeddingStore.builder()
                .directory(FSDirectory.open(Paths.get(embeddingStoreConfig.getPath())))
                .build();
    }
}
