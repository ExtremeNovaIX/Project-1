package p1.component.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import p1.model.ChatMessageEntity;
import p1.model.DialogueMessageEntity;
import p1.model.enums.DialogueMemoryStatus;
import p1.model.enums.DialogueMessageRole;
import p1.repo.ChatMessageRepository;
import p1.repo.DialogueMessageRepository;
import p1.utils.ChatMessageUtil;
import p1.utils.TimedMessageUtil;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageAppender {

    private final ChatMessageRepository repository;
    private final DialogueMessageRepository dialogueMessageRepository;

    private Long batchSequence;

    @Async("asyncTaskExecutor")
    public void appendAsync(String sessionId, ChatMessage message, Long dialogueBatchId) {
        try {
            // 全量保存至日志库
            ChatMessageEntity entity = new ChatMessageEntity(message, sessionId);
            entity.setTime(resolveTime(ChatMessageUtil.extractText(message)));
            repository.save(entity);

            // 过滤出正式对话消息并保存至对话消息库
            DialogueMessageEntity dialogueMessage = toDialogueMessage(sessionId, message, dialogueBatchId);
            if (dialogueMessage != null) {
                dialogueMessageRepository.save(dialogueMessage);
            }
        } catch (Exception e) {
            log.error("[消息写入失败] 保存聊天消息时发生异常。", e);
        }
    }

    public synchronized Long nextDialogueBatchId() {
        if (batchSequence == null) {
            batchSequence = dialogueMessageRepository.findMaxBatchId();
        }
        batchSequence = batchSequence + 1;
        return batchSequence;
    }

    public synchronized void markBatchProcessing(Long batchId) {
        if (batchId == null) {
            return;
        }

        // 通过batchId获取当前批次的所有对话消息
        List<DialogueMessageEntity> batchMessages = dialogueMessageRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        if (batchMessages.isEmpty()) {
            return;
        }

        // 标记所有对话消息为 PROCESSING 状态
        batchMessages.forEach(message -> message.setMemoryStatus(DialogueMemoryStatus.PROCESSING));
        dialogueMessageRepository.saveAll(batchMessages);
        log.info("[对话批次处理中] batchId={}，标记 {} 条原始对话消息为 PROCESSING。", batchId, batchMessages.size());
    }

    public DialogueBatch loadBatch(Long batchId) {
        if (batchId == null) {
            return null;
        }

        List<DialogueMessageEntity> batchMessages = dialogueMessageRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        if (batchMessages.isEmpty()) {
            return null;
        }
        return new DialogueBatch(batchId, batchMessages);
    }

    public synchronized void archiveDialogueBatch(Long batchId) {
        if (batchId == null) {
            return;
        }

        // 通过batchId获取当前批次的所有对话消息
        List<DialogueMessageEntity> batchMessages = dialogueMessageRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        if (batchMessages.isEmpty()) {
            return;
        }

        // 标记所有对话消息为 ARCHIVED 状态
        LocalDateTime archivedAt = LocalDateTime.now();
        batchMessages.forEach(message -> {
            message.setMemoryStatus(DialogueMemoryStatus.ARCHIVED);
            message.setArchivedAt(archivedAt);
        });
        dialogueMessageRepository.saveAll(batchMessages);

        log.info("[对话批次归档] batchId={}，标记 {} 条原始对话消息为 ARCHIVED。", batchId, batchMessages.size());
    }

    public synchronized DialogueBatch claimAllPendingDialogueBatch(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        List<DialogueMessageEntity> pendingMessages = dialogueMessageRepository
                .findBySessionIdAndMemoryStatusOrderByCreatedAtAsc(sessionId, DialogueMemoryStatus.PENDING);

        if (pendingMessages.isEmpty()) {
            return null;
        }

        long batchId = nextDialogueBatchId();
        pendingMessages.forEach(message -> {
            message.setBatchId(batchId);
            message.setMemoryStatus(DialogueMemoryStatus.PROCESSING);
        });
        dialogueMessageRepository.saveAll(pendingMessages);

        log.info("[遗留批次领取] sessionId={}，batchId={}，领取 {} 条未归档对话消息进行补压缩。",
                sessionId, batchId, pendingMessages.size());
        return new DialogueBatch(batchId, pendingMessages);
    }

    private DialogueMessageEntity toDialogueMessage(String sessionId, ChatMessage message, Long dialogueBatchId) {
        if (message instanceof UserMessage) {
            return buildDialogueMessage(sessionId, DialogueMessageRole.USER, ChatMessageUtil.extractText(message), dialogueBatchId);
        }

        if (ChatMessageUtil.isAiFinalResponseMessage(message)) {
            return buildDialogueMessage(sessionId, DialogueMessageRole.ASSISTANT, ChatMessageUtil.extractText(message), dialogueBatchId);
        }

        return null;
    }

    private DialogueMessageEntity buildDialogueMessage(String sessionId, DialogueMessageRole role, String text, Long dialogueBatchId) {
        String cleanText = text == null ? "" : text.trim();
        if (cleanText.isBlank()) {
            return null;
        }

        DialogueMessageEntity entity = new DialogueMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole(role);
        entity.setTextContent(cleanText);
        entity.setCreatedAt(resolveTime(cleanText));
        entity.setBatchId(dialogueBatchId);
        entity.setMemoryStatus(DialogueMemoryStatus.PENDING);
        return entity;
    }

    private LocalDateTime resolveTime(String text) {
        LocalDateTime parsed = TimedMessageUtil.parse(text);
        return parsed == null ? LocalDateTime.now() : parsed;
    }

    public record DialogueBatch(Long batchId, List<DialogueMessageEntity> messages) {
    }
}
