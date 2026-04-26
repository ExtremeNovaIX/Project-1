package p1.component.agent.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import p1.component.agent.context.SummaryCacheManager;
import p1.component.agent.model.FactExtractionPipelineResult;

import java.util.List;

import static p1.utils.ChatMessageUtil.isAiFinalResponseMessage;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemoryAsyncCompressor {

    private static final int MIN_IMPORTANCE_SCORE = 5;

    private final SummaryCacheManager summaryCacheManager;
    private final FactExtractionService factExtractionService;
    private final MemoryWriteService memoryStorage;

    /**
     * 异步压缩链路：
     * 1. 只保留用户消息和 AI 最终答复；
     * 2. 提取事件并补全重要性评分；
     * 3. 过滤低分事件；
     * 4. 以“一次压缩批次”为单位写入存储侧 event-group。
     */
    @Async("asyncTaskExecutor")
    public void compressAsync(String sessionId,
                              List<ChatMessage> toCompress,
                              Runnable onSuccess,
                              Runnable onFailure) {
        log.info("[记忆压缩] sessionId={} 开始异步压缩，消息数={}", sessionId, toCompress.size());
        try {
            // 工具调用结果和中间推理不进入事实提取，避免把临时上下文误写进长期记忆。
            List<ChatMessage> pureChatHistory = toCompress.stream()
                    .filter(msg -> msg instanceof UserMessage || isAiFinalResponseMessage(msg))
                    .toList();

            List<FactExtractionService.ExtractedFactEventDTO> extractedEvents =
                    factExtractionService.extractFact(pureChatHistory, sessionId);
            log.info("[记忆压缩] sessionId={} 第一阶段事实提取完成，事件数={}", sessionId, extractedEvents.size());

            List<FactExtractionService.ExtractedFactEventDTO> importantEvents = extractedEvents.stream()
                    .filter(event -> keepEvent(sessionId, event))
                    .toList();

            if (importantEvents.isEmpty()) {
                log.info("[记忆压缩] sessionId={} 没有达到重要性阈值的事件", sessionId);
                if (onSuccess != null) {
                    onSuccess.run();
                }
                return;
            }

            FactExtractionService.FactSummaryDTO summary = factExtractionService.summarizeFacts(importantEvents);
            FactExtractionPipelineResult extractionResult =
                    factExtractionService.buildPipelineResult(importantEvents, summary);
            log.info("[记忆压缩] sessionId={} 第二阶段摘要完成，事件数={}，tagCount={}",
                    sessionId, extractionResult.events().size(), extractionResult.tags().size());

            memoryStorage.saveEventGroup(sessionId, extractionResult.events(), extractionResult.tags());

            String normalizedSummary = extractionResult.summary() == null ? "" : extractionResult.summary().trim();
            if (!normalizedSummary.isBlank()) {
                summaryCacheManager.updateSummary(sessionId, normalizedSummary);
            }

            if (onSuccess != null) {
                onSuccess.run();
            }
        } catch (Exception e) {
            log.error("[记忆压缩失败] sessionId={} 后台记忆压缩异常", sessionId, e);
            if (onFailure != null) {
                onFailure.run();
            }
        }
    }

    private boolean keepEvent(String sessionId, FactExtractionService.ExtractedFactEventDTO event) {
        if (event == null) {
            return false;
        }

        if (event.getImportanceScore() >= MIN_IMPORTANCE_SCORE) {
            return true;
        }

        log.info("[事件丢弃] sessionId={} 重要性不足，topic={}，score={}",
                sessionId, event.getTopic(), event.getImportanceScore());
        return false;
    }

}
