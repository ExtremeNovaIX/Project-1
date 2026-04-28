package p1.service;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import p1.component.ai.assistant.CharacterPromptRegistry;
import p1.component.ai.assistant.FrontendAssistant;
import p1.component.ai.assistant.FrontendChatRequestAugmentor;
import p1.component.ai.memory.SummaryCacheManager;
import p1.component.ai.tools.MemorySearchTools;
import p1.config.prop.AssistantProperties;
import p1.config.runtime.RuntimeModelSettingsRegistry;
import p1.infrastructure.vector.SessionMemoryVectorStoreFactory;
import p1.model.dto.ChatRequestDTO;
import p1.service.markdown.MemoryArchiveMarkdownService;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final FrontendAssistant frontendAssistant;
    private final CharacterPromptRegistry characterPromptRegistry;
    private final SummaryCacheManager summaryCacheManager;
    private final AssistantProperties assistantProperties;
    private final ChatMemoryProvider chatMemoryProvider;
    private final FrontendChatRequestAugmentor frontendChatRequestAugmentor;
    private final MemorySearchTools memorySearchTools;
    private final SessionMemoryVectorStoreFactory vectorStoreFactory;
    private final MemoryArchiveMarkdownService archiveMarkdownService;
    private final RuntimeModelSettingsRegistry runtimeModelSettingsRegistry;

    public String sendChatToLLM(ChatRequestDTO request) {
        runtimeModelSettingsRegistry.remember(request);
        String sessionId = request.getSessionId();
        String userMessage = request.getMessage();
        String rolePrompt = characterPromptRegistry.getPrompt(request.getCharacterName());
        String currentSummary = summaryCacheManager.getSummary(sessionId);
        FrontendAssistant assistant = hasModelOverride(request)
                ? buildRequestScopedAssistant(request)
                : frontendAssistant;
        return assistant.chat(sessionId, userMessage, rolePrompt, currentSummary);
    }

    private boolean hasModelOverride(ChatRequestDTO request) {
        return hasAiOverride(request) || hasEmbeddingOverride(request);
    }

    private boolean hasAiOverride(ChatRequestDTO request) {
        return StringUtils.hasText(request.getAiBaseUrl())
                || StringUtils.hasText(request.getAiApiKey())
                || StringUtils.hasText(request.getAiModelName());
    }

    private boolean hasEmbeddingOverride(ChatRequestDTO request) {
        return StringUtils.hasText(request.getEmbeddingBaseUrl())
                || StringUtils.hasText(request.getEmbeddingApiKey())
                || StringUtils.hasText(request.getEmbeddingModelName());
    }

    private FrontendAssistant buildRequestScopedAssistant(ChatRequestDTO request) {
        AssistantProperties.ChatModelConfig defaults = assistantProperties.activeChatModel();
        String baseUrl = firstText(request.getAiBaseUrl(), defaults.getBaseUrl());
        String apiKey = firstText(request.getAiApiKey(), defaults.getApiKey());
        String modelName = firstText(request.getAiModelName(), defaults.getModelName());
        long timeoutSeconds = defaults.getTimeoutSeconds() == null ? 300L : defaults.getTimeoutSeconds();

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .temperature(0.8)
                .logRequests(defaults.isLogEnabled())
                .logResponses(defaults.isLogEnabled());

        if (StringUtils.hasText(apiKey)) {
            builder.apiKey(apiKey);
        }

        ChatModel chatModel = builder.build();
        MemorySearchTools tools = hasEmbeddingOverride(request)
                ? buildRequestScopedMemorySearchTools(request)
                : memorySearchTools;
        return AiServices.builder(FrontendAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .chatRequestTransformer(frontendChatRequestAugmentor::augment)
                .tools(tools)
                .build();
    }

    private MemorySearchTools buildRequestScopedMemorySearchTools(ChatRequestDTO request) {
        AssistantProperties.EmbeddingModelConfig defaults = assistantProperties.activeEmbeddingModel();
        String baseUrl = firstText(request.getEmbeddingBaseUrl(), defaults.getBaseUrl());
        String apiKey = firstText(request.getEmbeddingApiKey(), defaults.getApiKey());
        String modelName = firstText(request.getEmbeddingModelName(), defaults.getModelName());

        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName);

        if (StringUtils.hasText(apiKey)) {
            builder.apiKey(apiKey);
        }

        EmbeddingModel embeddingModel = builder.build();
        EmbeddingService embeddingService = new EmbeddingService(
                vectorStoreFactory,
                embeddingModel,
                assistantProperties,
                runtimeModelSettingsRegistry
        );
        ArchiveEmbeddingService archiveEmbeddingService =
                new ArchiveEmbeddingService(embeddingService, archiveMarkdownService);
        return new MemorySearchTools(archiveEmbeddingService);
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
