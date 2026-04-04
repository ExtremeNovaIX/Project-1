package p1.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatPageController {

    @GetMapping({"/api/chat", "/api/chat/"})
    public String chatPage() {
        return "forward:/chat-ui/index.html";
    }
}
