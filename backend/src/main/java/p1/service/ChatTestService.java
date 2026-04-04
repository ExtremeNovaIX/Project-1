package p1.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import p1.component.ai.assistant.TestAssistant;
import p1.model.ChatRequestDTO;
import p1.model.TestChatResponseDTO;
import p1.model.TestChatTurnDTO;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatTestService {

    private final ChatService chatService;
    private final TestAssistant testAssistant;

    public TestChatResponseDTO runTestChat(Integer rounds) {
        String sessionId = "1";
        List<TestChatTurnDTO> messages = new ArrayList<>();

        for (int round = 1; round <= rounds; round++) {
            String transcript = buildTranscript(messages);
            String userMessage = testAssistant.nextUserMessage(transcript).trim();
            messages.add(new TestChatTurnDTO(round, "user", userMessage));

            ChatRequestDTO chatRequest = new ChatRequestDTO();
            chatRequest.setSessionId(sessionId);
            chatRequest.setMessage(userMessage);
            chatRequest.setCharacterName("test");
            chatRequest.setShortMode(false);

            String assistantReply = chatService.sendChatToLLM(chatRequest).trim();
            messages.add(new TestChatTurnDTO(round, "assistant", assistantReply));
        }

        return new TestChatResponseDTO(sessionId, rounds, messages);
    }

    private String buildTranscript(List<TestChatTurnDTO> messages) {
        if (messages.isEmpty()) {
            return "当前还没有对话，请直接开始第一句用户发言。";
        }

        StringBuilder transcript = new StringBuilder();
        for (TestChatTurnDTO message : messages) {
            transcript.append(message.getRole())
                    .append(": ")
                    .append(message.getContent())
                    .append("\n");
        }
        return transcript.toString();
    }
}
