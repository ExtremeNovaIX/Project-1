package p1.component.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p1.model.MemoryArchiveEntity;
import p1.model.UserPreferenceEntity;
import p1.repo.UserPreferenceRepository;
import p1.service.EmbeddingService;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@AllArgsConstructor
public class MemorySearchTools {

    private final UserPreferenceRepository userRepo;
    private final EmbeddingService embeddingService;

    @Tool("""
            在数据库中查询用户的核心档案信息，例如姓名、生日、基础喜好，如果查不到则会在内部调用向量检索方法。
            当用户询问身份、强相关事实答案时优先使用，尽量把查询意图压缩为核心关键词。
            可能会返回多个结果，请选择和当前语境最相关的内容。
            """)
    public String getCoreFact(String keyword) {
        String cleanKey = keyword.replaceAll("[，。！？!,.?]", "").strip();
        log.info("LLM开始调用 getCoreFact，关键词：{}", cleanKey);

        List<UserPreferenceEntity> matches = userRepo.findBySmartMatch(cleanKey);
        if (!matches.isEmpty()) {
            String facts = matches.stream()
                    .sorted((a, b) -> {
                        int diffA = Math.abs(a.getAliases().length() - cleanKey.length());
                        int diffB = Math.abs(b.getAliases().length() - cleanKey.length());
                        return Integer.compare(diffA, diffB);
                    })
                    .limit(3)
                    .map(item -> String.format("别名：%s；内容：%s", item.getAliases(), item.getConfigValue()))
                    .collect(Collectors.joining("；"));

            log.info("LLM调用 getCoreFact 完成，命中数量：{}，结果摘要：{}", matches.size(), facts);
            return "LLM调用 getCoreFact 结果：查询成功。与关键词“" + cleanKey + "”相关的档案信息如下：" + facts;
        }

        log.info("LLM调用 getCoreFact 完成，数据库未命中，转向向量检索，关键词：{}", cleanKey);
        return "数据库未命中，转向向量检索：" + searchLongTermMemory(keyword);
    }

    @Tool("""
            在过往记忆中进行向量化语义搜索，适用于查询过去的经历、感受、聊天片段或模糊回忆。
            可能会返回多个结果，请选择和当前语境最相关的内容。
            """)
    public String searchLongTermMemory(String query) {
        log.info("LLM开始调用 searchLongTermMemory，查询词：{}", query);
        List<EmbeddingService.MemoryArchiveMatch> matches = embeddingService.searchMemoryArchives(query, 3, 0.5);

        if (matches.isEmpty()) {
            log.info("LLM调用 searchLongTermMemory 完成，结果为未命中");
            return "LLM调用 searchLongTermMemory 结果：未找到与“" + query + "”相关的长期记忆。";
        }

        StringBuilder resultBuilder = new StringBuilder();
        for (EmbeddingService.MemoryArchiveMatch match : matches) {
            MemoryArchiveEntity archive = match.archive();
            String detailedSummary = normalize(archive.getDetailedSummary());
            if (detailedSummary.isBlank()) {
                detailedSummary = normalize(archive.getKeywordSummary());
            }

            resultBuilder.append("[记忆ID: ").append(archive.getId()).append("] ")
                    .append(detailedSummary).append("\n");
        }

        String memoryText = resultBuilder.toString();
        log.info("LLM调用 searchLongTermMemory 成功，命中数量：{}，结果摘要：{}", matches.size(), memoryText);
        return "LLM调用 searchLongTermMemory 结果：查询成功。检索到的相关长期记忆如下：" + memoryText;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
