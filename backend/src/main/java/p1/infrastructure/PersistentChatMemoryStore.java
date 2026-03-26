package p1.infrastructure;

import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p1.model.ChatMessageEntity;
import p1.repo.ChatMessageRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private final ChatMessageRepository repository;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<ChatMessageEntity> entities = repository.findBySessionIdOrderByCreatedAtAsc(memoryId.toString());
        return entities.stream()
                .map(entity -> ChatMessageDeserializer.messageFromJson(entity.getContent()))
                .collect(Collectors.toList());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String sessionId = memoryId.toString(); 
        repository.deleteBySessionId(sessionId); 

        LocalDateTime now = LocalDateTime.now(); 
        List<ChatMessageEntity> entities = new ArrayList<>(); 

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i); 
            ChatMessageEntity entity = new ChatMessageEntity(); 
            entity.setSessionId(sessionId); 

            String msgJson = ChatMessageSerializer.messageToJson(msg);
            entity.setContent(msgJson);

            entity.setRole(msg.type().toString());

            entity.setTime(now.plusNanos(i)); 
            entity.setCreatedAt(now.plusNanos(i)); 
            entities.add(entity); 
        }
        repository.saveAll(entities); 
    }

    @Override
    public void deleteMessages(Object memoryId) {
        repository.deleteBySessionId(memoryId.toString());
    }
}
