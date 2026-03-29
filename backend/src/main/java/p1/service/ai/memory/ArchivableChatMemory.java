package p1.service.ai.memory;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
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

    private final int triggerThreshold = 20; // 触发压缩的阈值
    private final int compressCount = 10;    // 每次压缩抽取的条数

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

        // 如果当前要添加的是大模型的最终文本回复且没有包含新的工具请求，则清理
        if (message instanceof AiMessage aiMsg && aiMsg.text() != null && !aiMsg.hasToolExecutionRequests()) {
            purifyContext();
        }

        messages.add(message);
        dbAppender.appendAsync(sessionId, message);
        if (messages.size() >= triggerThreshold && isCompressing.compareAndSet(false, true)) {
            log.info("ID为{}的LLM记忆触达压缩水位线 {}，触发记忆压缩...", sessionId, triggerThreshold);
            List<ChatMessage> toCompress = new ArrayList<>(messages.subList(0, compressCount));

            compressor.compressAsync(sessionId, toCompress, () -> {
                log.info("ID为{}的LLM记忆后台压缩完成，当前记忆条数为 {} 条", sessionId, messages.size());
                messages.removeAll(toCompress);
                isCompressing.set(false);
            });
        }
    }

    private void purifyContext() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);

            if (msg instanceof ToolExecutionResultMessage ||
                    (msg instanceof AiMessage && ((AiMessage) msg).hasToolExecutionRequests())) {
                // 清理调用tool产生的中间消息
                messages.remove(i);
            } else if (msg instanceof UserMessage) {
                // 扫到了用户说的话，说明当前这一个对话回合已经到头了，停止扫描
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
