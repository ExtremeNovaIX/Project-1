package p1.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;
import p1.mdc.ChatSessionMetrics;
import p1.model.ChatRequestDTO;
import p1.model.TestChatResponseDTO;
import p1.service.ChatService;
import p1.service.ChatTestService;

import java.util.Arrays;
import java.util.List;

import static p1.utils.SessionUtil.normalizeSessionId;

@RestController
@CrossOrigin
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final ChatTestService chatTestService;
    private final ChatSessionMetrics chatSessionMetrics;

    @PostMapping("/send")
    public List<String> send(@RequestBody ChatRequestDTO request) {
        String sessionId = normalizeSessionId(request.getSessionId());
        request.setSessionId(sessionId);
        int currentRound = chatSessionMetrics.incrementAndGetRound(sessionId);

        MDC.put("sessionId", sessionId);
        MDC.put("chatRound", String.valueOf(currentRound));
        try {
            String rawReply = chatService.sendChatToLLM(request);
            List<String> replyList;
            if (request.isShortMode()) {
                String splitRegex = "(?<=[。！？?!;；…])(?![。！？?!;；…])|(?<=\\.)(?![。！？?!;；…\\.0-9])|(?=\\[)";
                replyList = Arrays.stream(rawReply.split(splitRegex))
                        .filter(s -> !s.isBlank())
                        .map(String::trim)
                        .map(s -> s.replace("。", ""))
                        .filter(s -> !s.isBlank())
                        .toList();
            } else {
                replyList = List.of(rawReply);
            }
            return replyList;
        } finally {
            MDC.remove("chatRound");
            MDC.remove("sessionId");
        }
    }

    @GetMapping("/test")
    public TestChatResponseDTO test(@RequestParam Integer rounds) {
        return chatTestService.runTestChat(rounds);
    }
}
