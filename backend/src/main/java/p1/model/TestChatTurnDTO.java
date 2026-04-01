package p1.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestChatTurnDTO {
    private int round;
    private String role;
    private String content;
}
