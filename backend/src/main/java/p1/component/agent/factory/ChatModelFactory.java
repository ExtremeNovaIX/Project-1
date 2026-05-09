package p1.component.agent.factory;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import p1.config.prop.AssistantProperties;
import p1.utils.HttpUtil;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

@Component
public class ChatModelFactory {

    public ChatModel buildChatModel(AssistantProperties.ChatModelConfig config,
                                    ChatModelListener listener,
                                    Double temperature) {
        return buildChatModel(config, listener, temperature, false);
    }

    public ChatModel buildChatModel(AssistantProperties.ChatModelConfig config,
                                    ChatModelListener listener,
                                    Double temperature,
                                    boolean structuredOutputFriendly) {
        boolean responseFormatJsonSchemaSupported = supportsResponseFormatJsonSchema(config);
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .logRequests(config.isLogEnabled())
                .logResponses(config.isLogEnabled());

        if (StringUtils.hasText(config.getApiKey())) {
            builder.apiKey(config.getApiKey());
        }

        if (supportsDeepSeekThinkingToggle(config)) {
            builder.customParameters(Map.of("thinking", Map.of("type", "disabled")));
        }

        if (HttpUtil.isLocalEndpoint(config.getBaseUrl())) {
            builder.httpClientBuilder(HttpUtil.getLocalClientBuilder(Duration.ofSeconds(config.getTimeoutSeconds())));
        }

        if (listener != null) {
            builder.listeners(Collections.singletonList(listener));
        }
        if (temperature != null) {
            builder.temperature(temperature);
        }
        if (structuredOutputFriendly) {
            builder.parallelToolCalls(false);
            if (responseFormatJsonSchemaSupported) {
                builder.supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                        .strictJsonSchema(true);
            }
        }
        return builder.build();
    }

    private boolean supportsResponseFormatJsonSchema(AssistantProperties.ChatModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return false;
        }
        try {
            String host = URI.create(baseUrl).getHost();
            if (host == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase();
            return normalizedHost.endsWith("openai.com");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean supportsDeepSeekThinkingToggle(AssistantProperties.ChatModelConfig config) {
        String baseUrl = config.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return false;
        }
        try {
            String host = URI.create(baseUrl).getHost();
            if (host == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase();
            return normalizedHost.endsWith("deepseek.com");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
