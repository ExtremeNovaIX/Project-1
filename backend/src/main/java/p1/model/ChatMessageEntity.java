package p1.model;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
public class ChatMessageEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPRESSING = "COMPRESSING";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId;

    private LocalDateTime time;

    private String role;

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime createdAt = LocalDateTime.now();

    // 状态枚举：ACTIVE(活跃), COMPRESSING(压缩中), ARCHIVED(已归档)
    @Column(length = 20)
    private String status = STATUS_ACTIVE;

    public ChatMessageEntity(ChatMessage message, String sessionId) {
        this.setSessionId(sessionId);
        this.setRole(message.type().toString());
        this.setContent(ChatMessageSerializer.messageToJson(message));
        this.setTime(LocalDateTime.now());
        this.setStatus(STATUS_ARCHIVED);
    }

}