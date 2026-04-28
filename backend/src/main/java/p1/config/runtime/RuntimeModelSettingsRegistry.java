package p1.config.runtime;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import p1.model.dto.ChatRequestDTO;
import p1.utils.SessionUtil;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RuntimeModelSettingsRegistry {

    private final Map<String, RuntimeModelSettings> settingsBySession = new ConcurrentHashMap<>();

    public void remember(ChatRequestDTO request) {
        if (request == null) {
            return;
        }
        String sessionId = SessionUtil.normalizeSessionId(request.getSessionId());
        if (!hasAnyOverride(request)) {
            return;
        }
        settingsBySession.put(sessionId, new RuntimeModelSettings(
                trim(request.getAiBaseUrl()),
                trim(request.getAiApiKey()),
                trim(request.getAiModelName()),
                trim(request.getEmbeddingBaseUrl()),
                trim(request.getEmbeddingApiKey()),
                trim(request.getEmbeddingModelName())
        ));
    }

    public Optional<RuntimeModelSettings> find(String sessionId) {
        return Optional.ofNullable(settingsBySession.get(SessionUtil.normalizeSessionId(sessionId)));
    }

    private boolean hasAnyOverride(ChatRequestDTO request) {
        return StringUtils.hasText(request.getAiBaseUrl())
                || StringUtils.hasText(request.getAiApiKey())
                || StringUtils.hasText(request.getAiModelName())
                || StringUtils.hasText(request.getEmbeddingBaseUrl())
                || StringUtils.hasText(request.getEmbeddingApiKey())
                || StringUtils.hasText(request.getEmbeddingModelName());
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
