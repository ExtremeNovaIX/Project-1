package p1.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p1.component.agent.core.CharacterPromptRegistry;
import p1.component.agent.core.RpAgent;
import p1.component.agent.context.SummaryCacheManager;
import p1.model.dto.ChatRequestDTO;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final RpAgent rpAgent;
    private final CharacterPromptRegistry characterPromptRegistry;
    private final SummaryCacheManager summaryCacheManager;

    public String sendChatToRpAgent(ChatRequestDTO request) {
        String sessionId = request.getSessionId();
        String userMessage = request.getMessage();
        String rolePrompt = characterPromptRegistry.getPrompt(request.getCharacterName());
        String currentSummary = summaryCacheManager.getSummary(sessionId);
        return rpAgent.chat(sessionId, userMessage, rolePrompt, currentSummary);
    }
}
