package p1.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import p1.mapper.ChatMessageRepository;
import p1.model.ChatMessageEntity;
import p1.model.ChatRequestDTO;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatModel chatModel;
    private final ChatMessageRepository repository;

    public String sendChat(ChatRequestDTO request) {
        List<ChatMessageEntity> historyEntities = repository.findBySessionIdOrderByCreatedAtAsc(request.getSessionId());
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("你是一个情感丰富的二次元助手。每句话前必须带上[心情]标签。"));

        for (ChatMessageEntity entity : historyEntities) {
            if ("USER".equals(entity.getRole())) {
                messages.add(UserMessage.from(entity.getContent()));
            } else {
                messages.add(AiMessage.from(entity.getContent()));
            }
        }

        messages.add(UserMessage.from(request.getMessage()));
        ChatResponse chatResponse = chatModel.chat(messages);
        String aiReply = chatResponse.aiMessage().text();

        saveMessage(request.getSessionId(), "USER", request.getMessage());
        saveMessage(request.getSessionId(), "AI", aiReply);
        return aiReply;
    }

    private void saveMessage(String sessionId, String role, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole(role);
        entity.setContent(content);
        repository.save(entity);
    }
}
