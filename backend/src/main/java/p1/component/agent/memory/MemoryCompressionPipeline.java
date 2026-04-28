package p1.component.agent.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p1.component.agent.memory.model.FactExtractionPipelineResult;

import java.util.List;
import java.util.Optional;

import static p1.utils.ChatMessageUtil.isAiFinalResponseMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryCompressionPipeline {

    private static final int MIN_IMPORTANCE_SCORE = 5;

    private final FactExtractionService factExtractionService;

    public Optional<FactExtractionPipelineResult> buildPipelineResult(String sessionId, List<ChatMessage> toCompress) {
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
            return Optional.empty();
        }

        FactExtractionService.FactSummaryDTO summary = factExtractionService.summarizeFacts(importantEvents);
        FactExtractionPipelineResult extractionResult =
                factExtractionService.buildPipelineResult(importantEvents, summary);
        log.info("[记忆压缩] sessionId={} 第二阶段摘要完成，事件数={}，tagCount={}",
                sessionId, extractionResult.events().size(), extractionResult.tags().size());
        return Optional.of(extractionResult);
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
