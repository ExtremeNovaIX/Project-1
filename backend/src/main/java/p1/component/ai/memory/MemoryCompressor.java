package p1.component.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import p1.component.ai.service.FactExtractionAiService;
import p1.component.ai.service.MemoryLogicJudgeAiService;
import p1.model.ExtractedMemoryEventDTO;
import p1.service.MemoryWriteService;

import java.util.List;

import static p1.utils.ChatMessageUtil.isAiFinalResponseMessage;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemoryCompressor {

    private final SummaryCacheManager summaryCacheManager;
    private final FactExtractionAiService factExtractorAiService;
    private final MemoryLogicJudgeAiService logicJudgeAiService;
    private final MemorySimilarityRouter similarityRouter;
    private final MemoryWriteService memoryStorage;

    @Async("asyncTaskExecutor")
    public void compressAsync(String sessionId,
                              List<ChatMessage> toCompress,
                              Runnable onSuccess,
                              Runnable onFailure) {
        log.info("[记忆压缩] 开始异步压缩，sessionId={}，消息数={}", sessionId, toCompress.size());
        try {
            List<ChatMessage> pureChatHistory = toCompress.stream()
                    .filter(msg -> msg instanceof UserMessage || isAiFinalResponseMessage(msg))
                    .toList();

            FactExtractionAiService.FactExtractionResponse response =
                    factExtractorAiService.extractAndMatchFacts(pureChatHistory);
            List<ExtractedMemoryEventDTO> events = response.getEvents() == null ? List.of() : response.getEvents();
            log.info("[记忆压缩] 提取到 {} 个事件，sessionId={}", events.size(), sessionId);

            for (ExtractedMemoryEventDTO event : events) {
                processMemoryEvent(sessionId, event);
            }

            summaryCacheManager.updateSummary(sessionId, response.getSummary());
            if (onSuccess != null) {
                onSuccess.run();
            }
        } catch (Exception e) {
            log.error("[记忆压缩失败] 后台记忆压缩异常，sessionId={}", sessionId, e);
            if (onFailure != null) {
                onFailure.run();
            }
        }
    }

    private void processMemoryEvent(String sessionId, ExtractedMemoryEventDTO event) {
        if (event.getImportanceScore() < 5) {
            log.info("[事件丢弃] 重要性不足，score={}，topic={}，narrative={}",
                    event.getImportanceScore(), event.getTopic(), event.getNarrative());
            return;
        }

        MemorySimilarityRouter.RoutingResult routeResult = similarityRouter.evaluate(event);
        switch (routeResult.action()) {
            case DISCARD -> log.info("[事件丢弃] 与已有记忆高度重复，topic={}，narrative={}",
                    event.getTopic(), event.getNarrative());

            case NEEDS_JUDGE -> {
                MemorySimilarityRouter.CandidateMemory candidate = routeResult.candidate();
                String judgeDecision = logicJudgeAiService.judgeLogic(candidate.text(), event.getNarrative());

                if (judgeDecision.trim().toUpperCase().contains("UPDATE")) {
                    log.info("[事件更新] topic={} 命中已有记忆，targetMemoryId={}，写入 patch",
                            event.getTopic(), candidate.dbId());
                    memoryStorage.patchMemory(candidate.dbId(), event.getNarrative());
                } else if (judgeDecision.trim().toUpperCase().contains("INSERT")) {
                    log.info("[事件新建] topic={} 与候选记忆差异较大，创建新记忆，candidateMemoryId={}",
                            event.getTopic(), candidate.dbId());
                    memoryStorage.saveNewMemory(sessionId, event.getTopic(), event.getKeywordSummary(), event.getNarrative());
                } else {
                    log.info("[事件丢弃] LLM 判断与已有记忆重复，topic={}，narrative={}",
                            event.getTopic(), event.getNarrative());
                }
            }

            case INSERT_NEW -> {
                log.info("[事件新建] topic={}，直接创建新记忆", event.getTopic());
                memoryStorage.saveNewMemory(sessionId, event.getTopic(), event.getKeywordSummary(), event.getNarrative());
            }
        }
    }
}
