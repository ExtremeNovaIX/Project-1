package p1.component.ai.memory;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import lombok.extern.slf4j.Slf4j;
import p1.config.prop.AssistantProperties;
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
    private final ChatMessageAppender dbAppender;

    private final int triggerThreshold;
    private final int compressCount;
    private final Set<String> referenceBuffer = ConcurrentHashMap.newKeySet();

    private volatile Long currentDialogueBatchId;

    public ArchivableChatMemory(String sessionId, MemoryCompressor compressor,
                                ChatMessageAppender dbAppender, AssistantProperties assistantProperties) {
        this.sessionId = sessionId;
        this.compressor = compressor;
        this.dbAppender = dbAppender;
        this.triggerThreshold = assistantProperties.getChatMemory().getTriggerCompressThreshold();
        this.compressCount = assistantProperties.getChatMemory().getCompressCount();
        this.currentDialogueBatchId = dbAppender.nextDialogueBatchId();
    }

    @Override
    public Object id() {
        return sessionId;
    }

    @Override
    public void add(ChatMessage message) {
        LocalDateTime time = LocalDateTime.now();
        ChatMessage chatMessage = ChatMessageUtil.withTimestamp(message, time);

        if (chatMessage instanceof SystemMessage) {
            messages.removeIf(existing -> existing instanceof SystemMessage);
            messages.addFirst(chatMessage);
            return;
        }

        boolean isFinalTurnMessage = ChatMessageUtil.isAiFinalResponseMessage(chatMessage);
        if (isFinalTurnMessage) {
            purifyContext();
        }

        dbAppender.appendAsync(sessionId, chatMessage, currentDialogueBatchId);
        messages.add(chatMessage);

        compressMemory(isFinalTurnMessage);
    }

    private void compressMemory(boolean isFinalTurnMessage) {
        if (isFinalTurnMessage && messages.size() >= triggerThreshold && isCompressing.compareAndSet(false, true)) {
            log.info("[记忆压缩触发] 当前消息数达到阈值 {}，准备压缩。", triggerThreshold);
            List<ChatMessage> toCompress = new ArrayList<>(messages.subList(0, compressCount));
            List<String> references = new ArrayList<>(referenceBuffer);

            Long batchIdToCompress = currentDialogueBatchId;
            currentDialogueBatchId = dbAppender.nextDialogueBatchId();
            dbAppender.markBatchProcessing(batchIdToCompress);

            compressor.compressAsync(sessionId, toCompress, references, () -> {
                // 压缩完成后，将当前批次的所有对话消息标记为 ARCHIVED 状态作为事务结尾
                dbAppender.archiveDialogueBatch(batchIdToCompress);

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
    }

    private void purifyContext() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);

            if (message instanceof ToolExecutionResultMessage toolMessage) {
                if (toolMessage.text() != null && toolMessage.text().contains("[记忆ID:")) {
                    referenceBuffer.add(toolMessage.text());
                    log.info("[记忆引用提取] 捕获带记忆 ID 的工具结果：{}", toolMessage.text());
                }
                messages.remove(i);
            } else if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                messages.remove(i);
            } else if (message instanceof UserMessage) {
                break;
            }
        }
        log.info("[记忆窗口净化] 当前窗口剩余 {} 条消息。", messages.size());
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
