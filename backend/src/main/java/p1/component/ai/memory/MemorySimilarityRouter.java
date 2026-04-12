package p1.component.ai.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p1.config.prop.AssistantProperties;
import p1.model.ExtractedMemoryEventDTO;
import p1.model.MemoryArchiveDocument;
import p1.model.enums.MemoryRouteAction;
import p1.service.EmbeddingService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemorySimilarityRouter {
    private final EmbeddingService embeddingService;
    private final AssistantProperties props;

    public RoutingResult evaluate(ExtractedMemoryEventDTO newEvent) {
        double THRESHOLD_DUPLICATE = props.getChatMemory().getDuplicationThreshold();
        double THRESHOLD_RELATED = props.getChatMemory().getRelatedThreshold();

        String retrievalQuery = normalize(newEvent.getKeywordSummary());
        if (retrievalQuery.isBlank()) {
            retrievalQuery = normalize(newEvent.getNarrative());
        }

        log.info("[相似度校验] 由新事件 [{}] 触发，查询文本：{}", newEvent.getTopic(), retrievalQuery);
        List<EmbeddingService.MemoryArchiveMatch> matches = embeddingService.searchMemoryArchives(retrievalQuery, 3, THRESHOLD_RELATED);

        if (matches.isEmpty()) {
            log.info("[相似度校验] 新事件 [{}] 与任何旧记忆相似度都较低，进入新增。", newEvent.getTopic());
            return new RoutingResult(MemoryRouteAction.INSERT_NEW, null);
        }

        EmbeddingService.MemoryArchiveMatch bestMatch = matches.getFirst();
            MemoryArchiveDocument archive = bestMatch.archive();
        double topScore = bestMatch.score();
        String oldNarrative = normalize(archive.getDetailedSummary());
        if (oldNarrative.isBlank()) {
            oldNarrative = normalize(archive.getKeywordSummary());
        }

        log.info("[相似度校验] 新事件 [{}] <-> 旧记忆 ID:{}，得分：{}", newEvent.getTopic(), archive.getId(), topScore);

        if (topScore >= THRESHOLD_DUPLICATE) {
            log.warn("[相似度校验] 新事件 [{}] 与旧记忆 ID:{} 的相似度过高，丢弃新事件。", newEvent.getTopic(), archive.getId());
            return new RoutingResult(MemoryRouteAction.DISCARD, null);
        } else if (topScore >= THRESHOLD_RELATED) {
            CandidateMemory candidate = new CandidateMemory(archive.getId(), oldNarrative);
            return new RoutingResult(MemoryRouteAction.NEEDS_JUDGE, candidate);
        }
        return new RoutingResult(MemoryRouteAction.INSERT_NEW, null);
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    public record RoutingResult(MemoryRouteAction action, CandidateMemory candidate) {
    }

    public record CandidateMemory(Long dbId, String text) {
    }
}
