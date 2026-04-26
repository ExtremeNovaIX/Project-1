package p1.component.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import p1.infrastructure.vector.ArchiveVectorLibrary;
import p1.model.document.MemoryArchiveDocument;
import p1.service.archive.ArchiveEmbeddingService;

import java.util.List;

import static p1.utils.SessionUtil.normalizeSessionId;

@Slf4j
@Component
@AllArgsConstructor
public class MemorySearchTools {

    private final ArchiveEmbeddingService archiveEmbeddingService;

    @Tool("""
            在长期记忆中进行语义检索，适用于查询过往经历、情绪变化、关系推进或模糊回忆。
            可能会返回多条结果，请选择与当前语境最相关的内容。
            """)
    public String searchLongTermMemory(@NonNull String query) {
        if (!StringUtils.hasText(query)) {
            return "记忆检索请求不能为空。";
        }
        String trimmedQuery = query.trim();

        String sessionId = normalizeSessionId(MDC.get("sessionId"));
        log.info("[记忆工具] 开始检索长期记忆，sessionId={}，query={}", sessionId, trimmedQuery);
        List<ArchiveEmbeddingService.ArchiveVectorMatch> matches =
                archiveEmbeddingService.searchArchiveMatches(sessionId, ArchiveVectorLibrary.ARCHIVE, trimmedQuery, 3, 0.5);

        if (matches.isEmpty()) {
            log.info("[记忆工具] 未检索到相关长期记忆，sessionId={}，query={}", sessionId, trimmedQuery);
            return "未检索到与“" + trimmedQuery + "”相关的长期记忆。";
        }

        StringBuilder resultBuilder = new StringBuilder("检索到的相关长期记忆如下：\n");
        for (ArchiveEmbeddingService.ArchiveVectorMatch match : matches) {
            MemoryArchiveDocument archive = match.archive();
            String detailedSummary = archive.getNarrative() == null ? "" : archive.getNarrative().trim();
            if (detailedSummary.isBlank()) {
                detailedSummary = archive.getKeywordSummary() == null ? "" : archive.getKeywordSummary().trim();
            }
            resultBuilder.append(detailedSummary).append("\n");
        }

        String result = resultBuilder.toString().trim();
        log.info("[记忆工具] 长期记忆检索完成，sessionId={}，命中数={}，query={}", sessionId, matches.size(), trimmedQuery);
        return result;
    }
}
