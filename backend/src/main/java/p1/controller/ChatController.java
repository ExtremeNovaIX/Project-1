package p1.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import p1.model.ChatRequestDTO;
import p1.model.TestChatResponseDTO;
import p1.service.ChatService;
import p1.service.ChatTestService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final ChatTestService chatTestService;

    @PostMapping("/send")
    public Map<String, List<String>> send(@RequestBody ChatRequestDTO request) {
        String rawReply = chatService.sendChatToLLM(request);
        List<String> replyList;
        if (request.isShortMode()) {
            //如果是短句模式，按中文或英文句号、感叹号拆分
            replyList = Arrays.stream(rawReply.split("[。！？!?\n]"))
                    .filter(s -> !s.isBlank())
                    .toList();
        } else {
            replyList = List.of(rawReply);
        }
        return Map.of("reply", replyList);
    }

    @GetMapping("/test")
    public TestChatResponseDTO test(@RequestParam Integer rounds) {
        return chatTestService.runTestChat(rounds);
    }
}
