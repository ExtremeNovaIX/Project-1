package p1.service.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import p1.model.ChatMessageEntity;
import p1.repo.ChatMessageRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageAppender {

    private final ChatMessageRepository repository;

    @Async
    public void appendAsync(String sessionId, ChatMessage message) {
        try {
            ChatMessageEntity entity = new ChatMessageEntity(message, sessionId);
            repository.save(entity);
        } catch (Exception e) {
            log.error("ID为{}的LLM:记忆保存失败", sessionId, e);
        }
    }
}