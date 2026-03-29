package p1.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TestChatResponseDTO {
    private String sessionId;
    private int rounds;
    private List<TestChatTurnDTO> messages;
}
