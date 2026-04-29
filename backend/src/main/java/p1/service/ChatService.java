package p1.service;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import p1.component.agent.context.RpRequestTimeAppender;
import p1.component.agent.context.SummaryCacheManager;
import p1.component.agent.core.CharacterPromptRegistry;
import p1.component.agent.core.RpAgent;
import p1.component.agent.tools.CallSolverTool;
import p1.config.prop.AssistantProperties;
import p1.config.runtime.RuntimeModelSettingsRegistry;
import p1.model.dto.ChatRequestDTO;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final RpAgent rpAgent;
    private final CharacterPromptRegistry characterPromptRegistry;
    private final SummaryCacheManager summaryCacheManager;
    private final AssistantProperties assistantProperties;
    private final ChatMemoryProvider chatMemoryProvider;
    private final RpRequestTimeAppender rpRequestTimeAppender;
    private final CallSolverTool callSolverTool;
    private final RuntimeModelSettingsRegistry runtimeModelSettingsRegistry;

    public String sendChatToRpAgent(ChatRequestDTO request) {
        runtimeModelSettingsRegistry.remember(request);
        String sessionId = request.getSessionId();
        String userMessage = request.getMessage();
        String rolePrompt = characterPromptRegistry.getPrompt(request.getCharacterName());
        String currentSummary = summaryCacheManager.getSummary(sessionId);
        RpAgent agent = hasAiOverride(request) ? buildRequestScopedAgent(request) : rpAgent;
        return agent.chat(sessionId, userMessage, rolePrompt, currentSummary);
    }

    public String sendChatToLLM(ChatRequestDTO request) {
        return sendChatToRpAgent(request);
    }

    private boolean hasAiOverride(ChatRequestDTO request) {
        return request != null
                && (StringUtils.hasText(request.getAiBaseUrl())
                || StringUtils.hasText(request.getAiApiKey())
                || StringUtils.hasText(request.getAiModelName()));
    }

    private RpAgent buildRequestScopedAgent(ChatRequestDTO request) {
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
        return AiServices.builder(RpAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .chatRequestTransformer(rpRequestTimeAppender::augment)
                .tools(callSolverTool)
                .build();
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
