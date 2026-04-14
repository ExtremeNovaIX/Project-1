package p1.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p1.component.ai.assistant.CharacterPromptRegistry;
import p1.component.ai.assistant.FrontendAssistant;
import p1.component.ai.memory.SummaryCacheManager;
import p1.model.ChatRequestDTO;


@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final FrontendAssistant frontendAssistant;
    private final CharacterPromptRegistry characterPromptRegistry;
    private final SummaryCacheManager summaryCacheManager;

    @Transactional
    public String sendChatToLLM(ChatRequestDTO request) {
        String sessionId = request.getSessionId();
        String userMessage = request.getMessage();
        String rolePrompt = characterPromptRegistry.getPrompt(request.getCharacterName());

        String currentSummary = summaryCacheManager.getSummary(sessionId);
        return frontendAssistant.chat(sessionId, userMessage, currentSummary, rolePrompt);
    }
}
