package p1.component.ai.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.extern.slf4j.Slf4j;
import p1.config.prop.AssistantProperties;
import p1.model.ChatLogEntity;
import p1.repo.db.ChatLogRepository;
import p1.service.markdown.DialogueMarkdownService;
import p1.utils.ChatMessageUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ArchivableChatMemory implements ChatMemory {

    private final String sessionId;
    private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isCompressing = new AtomicBoolean(false);

    private final MemoryCompressor compressor;
    private final ChatMessageAppender chatMessageAppender;
    private final DialogueMarkdownService dialogueMarkdownService;
    private final ChatLogRepository chatLogRepository;

    private final int triggerThreshold;
    private final int compressCount;
    private final Set<String> referenceBuffer = ConcurrentHashMap.newKeySet();

    public ArchivableChatMemory(String sessionId,
                                MemoryCompressor compressor,
                                ChatMessageAppender chatMessageAppender,
                                DialogueMarkdownService dialogueMarkdownService,
                                AssistantProperties assistantProperties,
                                ChatLogRepository chatLogRepository) {
        this.sessionId = sessionId;
        this.compressor = compressor;
        this.chatMessageAppender = chatMessageAppender;
        this.dialogueMarkdownService = dialogueMarkdownService;
        this.triggerThreshold = assistantProperties.getChatMemory().getTriggerCompressThreshold();
        this.compressCount = assistantProperties.getChatMemory().getCompressCount();
        this.chatLogRepository = chatLogRepository;
    }

    @Override
    public Object id() {
        return sessionId;
    }

    @Override
    public void add(ChatMessage message) {
        log.debug("消息队列新增: {}", message);
        LocalDateTime time = LocalDateTime.now();
        ChatMessage withTimeChatMessage = ChatMessageUtil.withTimestamp(message, time);

        // 保存消息到日志库
        ChatLogEntity entity = new ChatLogEntity(message, sessionId);
        entity.setTime(time);
        chatLogRepository.save(entity);

        // 保持系统提示词置顶
        if (withTimeChatMessage instanceof SystemMessage) {
            messages.removeIf(existing -> existing instanceof SystemMessage);
            messages.addFirst(withTimeChatMessage);
            return;
        }

        boolean isFinalTurnMessage = ChatMessageUtil.isAiFinalResponseMessage(withTimeChatMessage);
        if (isFinalTurnMessage) {
            log.trace("对话轮次结束，开始清理上下文。");
            purifyContext();
        }

        chatMessageAppender.append(sessionId, withTimeChatMessage);
        messages.add(withTimeChatMessage);

        compressMemory(isFinalTurnMessage);
    }

    private void compressMemory(boolean isFinalTurnMessage) {
        if (!isFinalTurnMessage || messages.size() < triggerThreshold || !isCompressing.compareAndSet(false, true)) {
            return;
        }

        log.info("[记忆压缩触发] 当前消息数达到阈值 {}，准备压缩。", triggerThreshold);
        List<ChatMessage> toCompress = new ArrayList<>(messages.subList(0, compressCount));
        List<String> references = new ArrayList<>(referenceBuffer);

        ChatMessageAppender.DialogueBatch processingBatch = dialogueMarkdownService
                .promoteCollectingToProcessingIfReady(sessionId, triggerThreshold, compressCount)
                .map(batch -> new ChatMessageAppender.DialogueBatch(batch.id(), batch.sessionId(), batch.messages()))
                .orElse(null);
        if (processingBatch == null) {
            isCompressing.set(false);
            return;
        }

        compressor.compressAsync(sessionId, toCompress, references, () -> {
            dialogueMarkdownService.acknowledgeProcessing(sessionId);

            for (int i = 0; i < toCompress.size(); i++) {
                if (!messages.isEmpty()) {
                    messages.removeFirst();
                }
            }
            referenceBuffer.clear();
            isCompressing.set(false);
            log.info("[记忆压缩完成] 当前内存窗口剩余 {} 条消息。", messages.size());
        });
    }

    private void purifyContext() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);

            if (message instanceof ToolExecutionResultMessage toolMessage) {
                if (toolMessage.text() != null && toolMessage.text().contains("[记忆ID:")) {
                    referenceBuffer.add(toolMessage.text());
                    log.debug("[上下文清理] 捕获带记忆 ID 的工具结果：{}", toolMessage.text());
                }
                messages.remove(i);
            } else if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                messages.remove(i);
            } else if (message instanceof UserMessage) {
                break;
            }
        }
        log.info("[上下文清理] 清理完成，当前窗口剩余 {} 条消息。", messages.size());
    }

    @Override
    public List<ChatMessage> messages() {
        return messages;
    }

    @Override
    public void clear() {
        messages.clear();
        isCompressing.set(false);
    }
}
