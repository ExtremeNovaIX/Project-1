package p1.model;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_logs")
@Data
@NoArgsConstructor
public class ChatLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId;

    private LocalDateTime time;

    private String role;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public ChatLogEntity(ChatMessage message, String sessionId) {
        this.sessionId = sessionId;
        this.role = message.type().toString();
        this.content = ChatMessageSerializer.messageToJson(message);
    }

}