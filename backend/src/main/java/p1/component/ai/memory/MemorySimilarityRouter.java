package p1.component.ai.memory;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import p1.config.prop.AssistantProperties;
import p1.model.ExtractedMemoryEventDTO;
import p1.model.enums.MemoryRouteAction;
import p1.service.EmbeddingService;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemorySimilarityRouter {
    private final EmbeddingService embeddingService;
    private final AssistantProperties props;

    public RoutingResult evaluate(ExtractedMemoryEventDTO newEvent) {
        double THRESHOLD_DUPLICATE = props.getChatMemory().getDuplicationThreshold(); // 去重线 (高于此值直接丢弃)
        double THRESHOLD_RELATED = props.getChatMemory().getRelatedThreshold();   // 关联线 (介于此值与去重线之间，触发LLM审核)

        // 在向量库中寻找最相似的 3 条旧记忆
        List<EmbeddingMatch<TextSegment>> matches = embeddingService.searchEmbedding(newEvent.getNarrative(), 3, THRESHOLD_RELATED).matches();

        if (matches == null || matches.isEmpty()) {
            return new RoutingResult(MemoryRouteAction.INSERT_NEW, null);
        }

        EmbeddingMatch<TextSegment> bestMatch = matches.getFirst();
        double topScore = bestMatch.score();

        Long matchedDbId = Long.parseLong(Objects.requireNonNull(bestMatch.embedded().metadata().getString("db_id")));
        String oldNarrative = bestMatch.embedded().text();

        log.info("相似度校验: 新事件 [{}] <-> 旧记忆 ID:{} , 得分: {}",
                newEvent.getTopic(), matchedDbId, topScore);

        if (topScore >= THRESHOLD_DUPLICATE) {
            log.warn("新事件 [{}] 与旧记忆 ID:{} 的相似度过高，丢弃新事件。", newEvent.getTopic(), matchedDbId);
            return new RoutingResult(MemoryRouteAction.DISCARD, null);
        } else if (topScore >= THRESHOLD_RELATED) {
            // 得分在 0.82 ~ 0.95 之间，打包成候选对象，交给LLM判断
            CandidateMemory candidate = new CandidateMemory(matchedDbId, oldNarrative);
            return new RoutingResult(MemoryRouteAction.NEEDS_JUDGE, candidate);
        }
        return new RoutingResult(MemoryRouteAction.INSERT_NEW, null);
    }

    public record RoutingResult(MemoryRouteAction action, CandidateMemory candidate) {
    }

    public record CandidateMemory(Long dbId, String text) {
    }
}