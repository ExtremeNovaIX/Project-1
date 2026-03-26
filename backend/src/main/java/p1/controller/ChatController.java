package p1.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import p1.model.ChatRequestDTO;
import p1.service.ChatService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/send")
    public Map<String, List<String>> send(@RequestBody ChatRequestDTO request) {
        String rawReply = chatService.sendChat(request);
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
}