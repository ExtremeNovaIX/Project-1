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

    @Async
    public void compressAsync(String sessionId, List<ChatMessage> toCompress, List<String> references, Runnable onSuccess) {
        log.info("ID为{}的LLM: 开始异步压缩 {} 条近期记忆...", sessionId, toCompress.size());
        try {
            String referenceStr = (references == null || references.isEmpty()) ? "无引用记忆" : String.join("\n", references);
            List<ExtractedMemoryEventDTO> events = factExtractorAiService.extractAndMatchFacts(toCompress, referenceStr).getEvents();
            log.info("ID为{}的LLM: 提取到 {} 条事件，引用记忆列表: [{}]。正在压缩处理事件中...", sessionId, events.size(), referenceStr);

            for (ExtractedMemoryEventDTO event : events) {
                processMemoryEvent(event);
            }

            String newIncrementalSummary = summarizeAiService.summarize(toCompress);
            summaryCacheManager.updateSummary(sessionId, newIncrementalSummary);
            if (onSuccess != null) onSuccess.run();
        } catch (Exception e) {
            log.error("ID为{}的LLM: 后台记忆压缩失败。", sessionId, e);
        }
    }

    public void processMemoryEvent(ExtractedMemoryEventDTO event) {
        MemorySimilarityRouter.RoutingResult routeResult = similarityRouter.evaluate(event);

        switch (routeResult.action()) {
            case DISCARD:
                log.info("主题为[{}]的事件销毁，原因为对已有记忆的高度重复。 事件描述: {}", event.getTopic(), event.getNarrative());
                break;

            case NEEDS_JUDGE:
                MemorySimilarityRouter.CandidateMemory candidate = routeResult.candidate();
                String judgeDecision = logicJudgeAiService.judgeLogic(candidate.text(), event.getNarrative());

                if ("UPDATE".equalsIgnoreCase(judgeDecision.trim())) {
                    memoryStorage.patchMemory(candidate.dbId(), event.getNarrative());
                    log.info("主题为[{}]的事件执行 Patch，目标 ID: {}。原因：是已有记忆的补充、纠正或更新。当前事件描述: {}；引用记忆描述: {}", event.getTopic(), candidate.dbId(), event.getNarrative(), candidate.text());
                } else {
                    memoryStorage.saveNewMemory(event.getTopic(), event.getNarrative());
                    log.info("主题为[{}]的事件执行新建记忆。原因：与已有相关记忆(ID：{}) 相差较大。当前事件描述: {}；引用记忆描述: {}", event.getTopic(), candidate.dbId(), candidate.text(), event.getNarrative());
                }
                break;

            case INSERT_NEW:
                memoryStorage.saveNewMemory(event.getTopic(), event.getNarrative());
                log.info("主题为[{}]的事件执行新建记忆。事件描述: {}", event.getTopic(), event.getNarrative());
                break;
        }
    }

}