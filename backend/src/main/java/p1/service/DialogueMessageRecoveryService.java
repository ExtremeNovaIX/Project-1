package p1.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import p1.component.ai.memory.ChatMessageAppender;
import p1.component.ai.memory.MemoryCompressor;
import p1.model.DialogueMessageEntity;
import p1.model.enums.DialogueMemoryStatus;
import p1.repo.DialogueMessageRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DialogueMessageRecoveryService {

    private final DialogueMessageRepository dialogueMessageRepository;
    private final MemoryCompressor memoryCompressor;
    private final ChatMessageAppender chatMessageAppender;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingDialogueMessages() {
        recoverUnfinishedBatches();
        recoverLegacyPendingSessions();
    }

    private void recoverUnfinishedBatches() {
        List<Long> batchIds = dialogueMessageRepository.findUnfinishedBatchIds();
        if (batchIds.isEmpty()) {
            return;
        }

        log.info("[遗留对话处理] 启动时发现 {} 个未完成批次。", batchIds.size());
        for (Long batchId : batchIds) {
            chatMessageAppender.markBatchProcessing(batchId);
            submitRecoveryCompression(chatMessageAppender.loadBatch(batchId));
        }
    }

    private void recoverLegacyPendingSessions() {
        List<DialogueMessageEntity> pendingMessages =
                dialogueMessageRepository.findByMemoryStatusAndBatchIdIsNullOrderByCreatedAtAsc(DialogueMemoryStatus.PENDING);

        if (pendingMessages.isEmpty()) {
            return;
        }

        List<String> sessionIds = pendingMessages.stream()
                .map(DialogueMessageEntity::getSessionId)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));

        log.info("[遗留对话处理] 启动时发现 {} 个 session 存在未分批的历史遗留对话。", sessionIds.size());
        for (String sessionId : sessionIds) {
            submitRecoveryCompression(chatMessageAppender.claimAllPendingDialogueBatch(sessionId));
        }
    }

    private void submitRecoveryCompression(ChatMessageAppender.DialogueBatch batch) {
        if (batch == null || batch.messages().isEmpty()) {
            return;
        }

        String sessionId = batch.messages().getFirst().getSessionId();
        List<ChatMessage> chatMessages = toChatMessages(batch.messages());
        if (chatMessages.isEmpty()) {
            return;
        }

        log.info("[遗留对话处理] sessionId={}，batchId={}，准备处理 {} 条对话消息。",
                sessionId, batch.batchId(), chatMessages.size());
        memoryCompressor.compressAsync(sessionId, chatMessages, List.of(), () ->
                chatMessageAppender.archiveDialogueBatch(batch.batchId())
        );
    }

    private List<ChatMessage> toChatMessages(List<DialogueMessageEntity> pendingMessages) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        for (DialogueMessageEntity pendingMessage : pendingMessages) {
            switch (pendingMessage.getRole()) {
                case USER -> chatMessages.add(UserMessage.from(pendingMessage.getTextContent()));
                case ASSISTANT -> chatMessages.add(AiMessage.from(pendingMessage.getTextContent()));
            }
        }
        return chatMessages;
    }
}
