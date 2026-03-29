package p1.service.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import p1.service.ai.BackendAssistant;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemoryCompressor {

    private final BackendAssistant backendAssistant;
    private final SummaryCacheManager summaryCacheManager;

    @Async
    public void compressAsync(String sessionId, List<ChatMessage> toCompress, Runnable onSuccess) {
        log.info("ID为{}的LLM: 开始异步压缩 {} 条近期记忆...", sessionId, toCompress.size());

        try {
            String newIncrementalSummary = backendAssistant.summarize(toCompress);
            summaryCacheManager.updateSummary(sessionId, newIncrementalSummary);
            if (onSuccess != null) {
                onSuccess.run();
            }
            log.info("ID为{}的LLM: 记忆压缩并替换完成。生成的新摘要: {}", sessionId, newIncrementalSummary);
        } catch (Exception e) {
            log.error("ID为{}的LLM: 后台记忆压缩失败。原因: {}", sessionId, e.getMessage());
        }
    }
}