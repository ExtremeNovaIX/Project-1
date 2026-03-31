package p1.component.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import p1.component.ai.service.FactExtractionAiService;
import p1.component.ai.service.SummarizeAiService;
import p1.component.ai.tools.MemorySaveTools;
import p1.model.ExtractedMemoryEventDTO;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemoryCompressor {
    private final SummaryCacheManager summaryCacheManager;

    private final FactExtractionAiService factExtractorAiService;
    private final SummarizeAiService summarizeAiService;

    private final MemorySaveTools memoryStorage;

    @Async
    public void compressAsync(String sessionId, List<ChatMessage> toCompress, List<String> references, Runnable onSuccess) {
        log.info("ID为{}的LLM: 开始异步压缩 {} 条近期记忆...", sessionId, toCompress.size());
        try {
            String referenceStr = (references == null || references.isEmpty()) ? "无引用记忆" : String.join("\n", references);

            List<ExtractedMemoryEventDTO> events = factExtractorAiService.extractAndMatchFacts(toCompress, referenceStr).getEvents();

            for (ExtractedMemoryEventDTO event : events) {
                Long finalTargetId = null;

                if (event.isUpdateToOldTopic() && event.getMatchedTargetId() != null && event.getMatchedTargetId() != -1L) {
                    Long llmGuessedId = event.getMatchedTargetId();

                    boolean isIdValid = references != null && references.stream()
                            .anyMatch(ref -> ref.contains("[记忆ID:" + llmGuessedId + "]"));

                    if (isIdValid) {
                        finalTargetId = llmGuessedId;
                        log.info("匹配到有效旧记忆 ID: {}", finalTargetId);
                    } else {
                        log.warn("LLM发生幻觉或匹配错误，尝试将补丁打给不存在的 ID: {}。已拦截并更改行为为新记忆。", llmGuessedId);
                    }
                }

                if (finalTargetId != null) {
                    memoryStorage.patchMemory(finalTargetId, event.getNarrative());
                } else {
                    memoryStorage.saveNewMemory(event.getTopic(), event.getNarrative());
                }
            }

            String newIncrementalSummary = summarizeAiService.summarize(toCompress);
            summaryCacheManager.updateSummary(sessionId, newIncrementalSummary);
            if (onSuccess != null) onSuccess.run();
        } catch (Exception e) {
            log.error("ID为{}的LLM: 后台记忆压缩失败。", sessionId, e);
        }
    }
}