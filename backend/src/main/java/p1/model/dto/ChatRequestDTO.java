package p1.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChatRequestDTO {
    private String message;
    private String sessionId;
    private String characterName;
    private boolean shortMode;
}
