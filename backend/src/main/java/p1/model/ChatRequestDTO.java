package p1.model;

import lombok.Data;

@Data
public class ChatRequestDTO {
    private String message;
    private String sessionId;
    private boolean shortMode;
}