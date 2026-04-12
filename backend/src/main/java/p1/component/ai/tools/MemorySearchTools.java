package p1.component.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p1.model.MemoryArchiveDocument;
import p1.service.EmbeddingService;

import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class MemorySearchTools {

    private final EmbeddingService embeddingService;

    @Tool("""
            使用向量检索查询用户的核心档案信息，例如姓名、生日、基础喜好。
            当用户询问身份、强相关事实答案时优先使用，尽量把查询意图压缩为高信息密度的一句话。
            可能会返回多个结果，请选择和当前语境最相关的内容。
            """)
    public String getCoreFact(String keyword) {
        String cleanKey = keyword.replaceAll("[，。！？,.?]", "").strip();
        log.info("[记忆工具] 开始查询核心事实，keyword={}", cleanKey);
        return searchLongTermMemory(cleanKey);
    }

    @Tool("""
            在长期记忆中进行语义检索，适用于查询过往经历、情绪变化、关系推进或模糊回忆。
            可能会返回多条结果，请选择与当前语境最相关的内容。
            """)
    public String searchLongTermMemory(String query) {
        log.info("[记忆工具] 开始检索长期记忆，query={}", query);
        List<EmbeddingService.MemoryArchiveMatch> matches = embeddingService.searchMemoryArchives(query, 3, 0.5);

        if (matches.isEmpty()) {
            log.info("[记忆工具] 未检索到相关长期记忆，query={}", query);
            return "未检索到与“" + query + "”相关的长期记忆。";
        }

        StringBuilder resultBuilder = new StringBuilder("检索到的相关长期记忆如下：\n");
        for (EmbeddingService.MemoryArchiveMatch match : matches) {
            MemoryArchiveDocument archive = match.archive();
            String detailedSummary = normalize(archive.getDetailedSummary());
            if (detailedSummary.isBlank()) {
                detailedSummary = normalize(archive.getKeywordSummary());
            }

            resultBuilder.append("[记忆ID: ")
                    .append(archive.getId())
                    .append("] ")
                    .append(detailedSummary)
                    .append("\n");
        }

        String result = resultBuilder.toString().trim();
        log.info("[记忆工具] 长期记忆检索完成，命中数量={}，query={}", matches.size(), query);
        return result;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
