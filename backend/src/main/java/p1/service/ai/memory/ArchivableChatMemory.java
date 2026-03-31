package p1.service.ai.memory;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@AllArgsConstructor
@Slf4j
public class ArchivableChatMemory implements ChatMemory {

    private final String sessionId;
    private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isCompressing = new AtomicBoolean(false);
    private final MemoryCompressor compressor;
    private final ChatMessageAppender dbAppender;

    private final int triggerThreshold = 30; // 触发压缩的阈值
    private final int compressCount = 20;    // 每次压缩抽取的条数
    private final Set<String> referenceBuffer = ConcurrentHashMap.newKeySet();

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
        log.info("ID为{}的LLM记忆窗口新增一条消息，当前记忆条数为 {} 条", sessionId, messages.size());

        if (isFinalTurnMessage && messages.size() >= triggerThreshold && isCompressing.compareAndSet(false, true)) {
            log.info("ID为{}的LLM记忆触达压缩水位线 {}，触发记忆压缩...", sessionId, triggerThreshold);
            List<ChatMessage> toCompress = new ArrayList<>(messages.subList(0, compressCount));
            List<String> references = new ArrayList<>(referenceBuffer);

            compressor.compressAsync(sessionId, toCompress, references, () -> {
                log.info("ID为{}的LLM记忆后台压缩完成，当前记忆条数为 {} 条", sessionId, messages.size());
                messages.removeAll(toCompress);
                referenceBuffer.clear();
                isCompressing.set(false);
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
