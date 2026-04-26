package p1.component.agent.memory.model;

import p1.infrastructure.markdown.model.DialogueBatchMessage;

import java.util.List;

public record DialogueBatch(String batchId, String sessionId, List<DialogueBatchMessage> messages) {
}