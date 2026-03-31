package p1.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestChatResponseDTO {
    private String sessionId;
    private int rounds;
    private List<TestChatTurnDTO> messages;
}
