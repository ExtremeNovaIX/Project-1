package p1.component.ai.memory;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import lombok.extern.slf4j.Slf4j;
import p1.config.prop.AssistantProperties;

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

    private final int triggerThreshold; // 触发压缩的阈值
    private final int compressCount;    // 每次压缩抽取的条数
    private final Set<String> referenceBuffer = ConcurrentHashMap.newKeySet();

    public ArchivableChatMemory(String sessionId, MemoryCompressor compressor,
                                ChatMessageAppender dbAppender, AssistantProperties assistantProperties) {
        this.sessionId = sessionId;
        this.compressor = compressor;
        this.dbAppender = dbAppender;
        this.triggerThreshold = assistantProperties.getChatMemory().getTriggerCompressThreshold();
        this.compressCount = assistantProperties.getChatMemory().getCompressCount();
    }

    @Override
    public Object id() {
        return sessionId;
    }

    @Override
    public void add(ChatMessage message) {
        // 刷新SystemMessage
        if (message instanceof SystemMessage) {
            messages.removeIf(m -> m instanceof SystemMessage);
            messages.addFirst(message);
            return;
        }

        boolean isFinalTurnMessage = (message instanceof AiMessage aiMsg
                && aiMsg.text() != null
                && !aiMsg.hasToolExecutionRequests());

        if (isFinalTurnMessage) {
            purifyContext();
        }

        messages.add(message);
        dbAppender.appendAsync(sessionId, message);

        if (isFinalTurnMessage && messages.size() >= triggerThreshold && isCompressing.compareAndSet(false, true)) {
            log.info("记忆触达压缩水位线 {}，触发记忆压缩...", triggerThreshold);
            List<ChatMessage> toCompress = new ArrayList<>(messages.subList(0, compressCount));
            List<String> references = new ArrayList<>(referenceBuffer);

            compressor.compressAsync(sessionId, toCompress, references, () -> {
                for (int i = 0; i < toCompress.size(); i++) {
                    if (!messages.isEmpty()) {
                        messages.removeFirst();
                    }
                }
                referenceBuffer.clear();
                isCompressing.set(false);
                log.info("记忆后台压缩完成，当前记忆条数为 {} 条", messages.size());
            });
        }
    }

    private void purifyContext() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);

            if (msg instanceof ToolExecutionResultMessage toolMsg) {
                if (toolMsg.text() != null && toolMsg.text().contains("[记忆ID:")) {
                    referenceBuffer.add(toolMsg.text());
                    log.info("从清理器中提取带有记忆ID参照的消息 {}，已放入缓冲池", toolMsg.text());
                }
                messages.remove(i);
            } else if (msg instanceof AiMessage && ((AiMessage) msg).hasToolExecutionRequests()) {
                messages.remove(i);
            } else if (msg instanceof UserMessage) {
                break;
            }
        }
        log.info("记忆窗口清理完成，当前记忆条数为 {} 条", messages.size());
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
