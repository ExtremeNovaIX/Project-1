package p1.component.ai.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p1.model.enums.DialogueMessageRole;
import p1.repo.markdown.model.DialogueBatchMessage;
import p1.service.markdown.DialogueMarkdownService;
import p1.utils.ChatMessageUtil;
import p1.utils.TimedMessageUtil;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageAppender {
    private final DialogueMarkdownService dialogueMarkdownService;

    public void append(String sessionId, ChatMessage message) {
        try {
            switch (message) {
                case UserMessage msg ->
                        handleAppend(sessionId, DialogueMessageRole.USER, ChatMessageUtil.extractText(msg));

                case AiMessage msg when ChatMessageUtil.isAiFinalResponseMessage(msg) ->
                        handleAppend(sessionId, DialogueMessageRole.ASSISTANT, ChatMessageUtil.extractText(msg));

                default -> log.trace("[忽略消息] 类型: {}", message.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.error("[消息写入失败] 保存聊天消息时发生异常。", e);
        }
    }

    private void handleAppend(String sessionId, DialogueMessageRole role, String text) {
        String cleanText = text == null ? "" : text.trim();
        if (cleanText.isBlank()) return;

        LocalDateTime createdAt = TimedMessageUtil.parse(cleanText);
        dialogueMarkdownService.appendDialogueMessage(sessionId, role, cleanText, createdAt);
    }

    public record DialogueBatch(String batchId, String sessionId, List<DialogueBatchMessage> messages) {
    }
}
