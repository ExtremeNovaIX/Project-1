package p1.config;

import dev.langchain4j.community.rag.content.retriever.lucene.LuceneEmbeddingStore;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
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
import p1.infrastructure.PersistentChatMemoryStore;
import p1.service.ai.Assistant;
import p1.service.ai.skills.AssistantTools;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class AiConfig {

    private final AssistantProperties props;
    private final PersistentChatMemoryStore persistenceStore;

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
    public ChatMemoryProvider chatMemoryProvider() {
        AssistantProperties.ChatMemoryConfig chatMemoryConfig = props.getChatMemory();
        return chatId -> MessageWindowChatMemory.builder()
                .id(chatId)
                .maxMessages(chatMemoryConfig.getMaxMessages())
                .chatMemoryStore(persistenceStore)
                .build();
    }

    @Bean
    public Assistant assistant(ChatModel chatModel, AssistantTools assistantTools, ChatMemoryProvider chatMemoryProvider) {
        return AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(assistantTools)
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
