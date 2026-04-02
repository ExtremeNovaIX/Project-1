package p1.component.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import p1.component.ai.service.FactExtractionAiService;
import p1.component.ai.service.MemoryLogicJudgeAiService;
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
    private final MemoryLogicJudgeAiService logicJudgeAiService;
    private final MemorySimilarityRouter similarityRouter;

    private final MemorySaveTools memoryStorage;

    @Async("asyncTaskExecutor")
    public void compressAsync(String sessionId, List<ChatMessage> toCompress, List<String> references, Runnable onSuccess) {
        log.info("[记忆压缩]开始异步压缩 {} 条近期记忆...", toCompress.size());
        try {
            String referenceStr = (references == null || references.isEmpty()) ? "无引用记忆" : String.join("\n", references);
            List<ExtractedMemoryEventDTO> events = factExtractorAiService.extractAndMatchFacts(toCompress, referenceStr).getEvents();
            log.info("[记忆压缩]提取到 {} 条事件。正在压缩处理事件中...", events.size());

            for (ExtractedMemoryEventDTO event : events) {
                processMemoryEvent(event);
            }

            String newIncrementalSummary = summarizeAiService.summarize(toCompress);
            summaryCacheManager.updateSummary(sessionId, newIncrementalSummary);
            if (onSuccess != null) onSuccess.run();
        } catch (Exception e) {
            log.error("[记忆压缩]后台记忆压缩失败。", e);
        }
    }

    public void processMemoryEvent(ExtractedMemoryEventDTO event) {
        MemorySimilarityRouter.RoutingResult routeResult = similarityRouter.evaluate(event);

        switch (routeResult.action()) {
            case DISCARD:
                log.info("[事件丢弃]已有记忆的高度重复。主题[{}]，\n当前事件描述: {}", event.getTopic(), event.getNarrative());
                break;

            case NEEDS_JUDGE:
                MemorySimilarityRouter.CandidateMemory candidate = routeResult.candidate();
                String judgeDecision = logicJudgeAiService.judgeLogic(candidate.text(), event.getNarrative());
                log.info("temp");

                if (judgeDecision.trim().toUpperCase().contains("UPDATE")) {
                    memoryStorage.patchMemory(candidate.dbId(), event.getNarrative());
                    log.info("[事件更新] 主题为[{}]的事件执行 Patch，目标 ID: {}。原因：是已有记忆的补充、纠正或更新。\n当前事件描述: {}；引用记忆描述: {}", event.getTopic(), candidate.dbId(), event.getNarrative(), candidate.text());
                } else {
                    memoryStorage.saveNewMemory(event.getTopic(), event.getNarrative());
                    log.info("[事件新建] 主题为[{}]的事件执行新建记忆。原因：与已有相关记忆(ID：{}) 相差较大。\n当前事件描述: {}；引用记忆描述: {}", event.getTopic(), candidate.dbId(), candidate.text(), event.getNarrative());
                }
                break;

            case INSERT_NEW:
                memoryStorage.saveNewMemory(event.getTopic(), event.getNarrative());
                log.info("[事件新建] 主题为[{}]的事件执行新建记忆。\n当前事件描述: {}", event.getTopic(), event.getNarrative());
                break;
        }
    }

}