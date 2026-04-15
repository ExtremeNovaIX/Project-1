package p1.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p1.component.ai.assistant.TestAssistant;
import p1.model.ChatRequestDTO;
import p1.model.TestChatResponseDTO;
import p1.model.TestChatTurnDTO;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestChatService {

    private final ChatService chatService;
    private final TestAssistant testAssistant;
    private final ChatTestSelfSummaryService chatTestSelfSummaryService;

    public TestChatResponseDTO runTestChat(Integer rounds) {
        String sessionId = "test";
        List<TestChatTurnDTO> messages = new ArrayList<>();
        log.info("测试开始，当前总测试轮数：{}", rounds);
        for (int round = 1; round <= rounds; round++) {
            log.info("当前测试轮数：{}", round);
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
            if (messages.size() >= 16) {
                for (int i = 0; i < 4; i++) {
                    messages.removeFirst();
                }
            }

            if (round % 20 == 0) {
                int startRound = round - 19;
                chatTestSelfSummaryService.appendRoundSummary(
                        sessionId,
                        startRound,
                        round,
                        collectOwnMessages(messages, startRound, round)
                );
            }
        }

        return new TestChatResponseDTO(sessionId, rounds, messages);
    }

    private List<String> collectOwnMessages(List<TestChatTurnDTO> messages, int startRound, int endRound) {
        return messages.stream()
                .filter(message -> "user".equals(message.getRole()))
                .filter(message -> message.getRound() >= startRound && message.getRound() <= endRound)
                .map(TestChatTurnDTO::getContent)
                .toList();
    }

    private String buildTranscript(List<TestChatTurnDTO> messages) {
        if (messages.isEmpty()) {
            return "你们现在是第一次对话，没有前置信息，你必须主动开启一个新话题。";
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
