package p1.service;

import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import p1.component.ai.memory.ChatMessageAppender;
import p1.component.ai.memory.MemoryCompressor;
import p1.config.prop.AssistantProperties;
import p1.repo.markdown.model.DialogueBatchDocument;
import p1.repo.markdown.model.DialogueBatchMessage;
import p1.service.markdown.DialogueMarkdownService;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DialogueMessageRecoveryService {

    private final MemoryCompressor memoryCompressor;
    private final DialogueMarkdownService dialogueMarkdownService;
    private final AssistantProperties assistantProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingDialogueMessages() {
        recoverMarkdownBatches();
    }

    /**
     * 启动恢复顺序：
     * 1. 优先恢复 processing.md，因为它代表上次已经进入执行中的批次。
     * 2. 如果没有 processing.md，再尝试把达到阈值的 collecting.md 提升为 processing.md。
     */
    private void recoverMarkdownBatches() {
        Set<String> sessionIds = dialogueMarkdownService.listSessionIdsWithOpenBatches();
        if (sessionIds.isEmpty()) {
            return;
        }

        log.info("[对话恢复] 发现 {} 个 session 存在未完成的对话批次，正在处理...", sessionIds.size());
        for (String sessionId : sessionIds) {
            ChatMessageAppender.DialogueBatch processingBatch = dialogueMarkdownService.findProcessing(sessionId)
                    .map(this::toDialogueBatch)
                    .orElse(null);
            if (processingBatch != null) {
                submitRecoveryCompression(processingBatch);
                continue;
            }

            dialogueMarkdownService
                    .promoteCollectingToProcessingIfReady(
                            sessionId,
                            assistantProperties.getChatMemory().getTriggerCompressThreshold(),
                            assistantProperties.getChatMemory().getCompressCount()
                    )
                    .map(this::toDialogueBatch)
                    .ifPresent(this::submitRecoveryCompression);
        }
    }

    private void submitRecoveryCompression(ChatMessageAppender.DialogueBatch batch) {
        if (batch == null || batch.messages().isEmpty()) {
            return;
        }

        List<ChatMessage> chatMessages = toChatMessages(batch.messages());
        if (chatMessages.isEmpty()) {
            log.warn("[对话恢复] sessionId={}, batchId={}, 没有消息恢复，跳过压缩",
                    batch.sessionId(), batch.batchId());
            return;
        }

        log.info("[对话恢复] sessionId={}, batchId={}, 待恢复消息数={}",
                batch.sessionId(), batch.batchId(), chatMessages.size());

        memoryCompressor.compressAsync(batch.sessionId(), chatMessages, List.of(), () -> {
            dialogueMarkdownService.acknowledgeProcessing(batch.sessionId());
            dialogueMarkdownService
                    .promoteCollectingToProcessingIfReady(
                            batch.sessionId(),
                            assistantProperties.getChatMemory().getTriggerCompressThreshold(),
                            assistantProperties.getChatMemory().getCompressCount()
                    )
                    .map(this::toDialogueBatch)
                    .ifPresent(this::submitRecoveryCompression);
        });
    }

    private ChatMessageAppender.DialogueBatch toDialogueBatch(DialogueBatchDocument document) {
        return new ChatMessageAppender.DialogueBatch(document.id(), document.sessionId(), document.messages());
    }

    private List<ChatMessage> toChatMessages(List<DialogueBatchMessage> pendingMessages) {
        return pendingMessages.stream()
                .map(DialogueBatchMessage::toChatMessage)
                .toList();
    }
}
