package p1.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
public class ChatRequestDTO {
    private String message;
    private String sessionId;
    private boolean shortMode;
}