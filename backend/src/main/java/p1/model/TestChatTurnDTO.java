package p1.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TestChatTurnDTO {
    private int round;
    private String role;
    private String content;
}
