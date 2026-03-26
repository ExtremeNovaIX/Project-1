package p1.service.ai.skills;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p1.model.UserPreferenceEntity;
import p1.repo.UserPreferenceRepository;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantTools {

    private final UserPreferenceRepository userRepo;
    private final EmbeddingStore<TextSegment> vectorStore;
    private final EmbeddingModel embeddingModel;

    @Tool("""
            在数据库中查询用户的核心档案信息，例如姓名、生日、基础喜好。
            当用户询问身份强相关的事实答案时优先使用，尽量把查询意图压缩为核心关键词。
            可能会返回多个结果，请选择和当前语境最相关的内容。
            工具返回结果后，请直接基于结果回答用户，最好不要反复调用同一个工具。
            """)
    public String getCoreFact(String keyword) {
        String cleanKey = keyword.replaceAll("[，。！!,.?？]", "").strip();
        log.info("LLM开始调用 getCoreFact ，关键词：{}", cleanKey);

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
            return "getCoreFact 结果：查询成功。与关键词“" + cleanKey + "”相关的档案信息如下：" + facts;
        }

        log.info("LLM调用 getCoreFact 完成，数据库未命中，转向向量检索，关键词：{}", cleanKey);
        return searchLongTermMemory(keyword);
    }

    @Tool("""
            在过往记忆中进行语义搜索，适用于查询过去的经历、感受、聊天片段或模糊回忆。
            可能会返回多个结果，请选择和当前语境最相关的内容。
            工具返回结果后，请直接基于结果回答用户，不要反复调用同一个工具。
            """)
    public String searchLongTermMemory(String query) {
        log.info("LLM开始调用 searchLongTermMemory ，查询词：{}", query);

        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(0.7)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = vectorStore.search(searchRequest);
        if (searchResult.matches().isEmpty()) {
            log.info("LLM调用 searchLongTermMemory 完成，结果为未命中");
            return "searchLongTermMemory 结果：未找到与“" + query + "”相关的长期记忆。";
        }

        String memoryText = searchResult.matches().stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("；"));

        log.info("LLM调用 searchLongTermMemory 成功，命中数量：{}，结果摘要：{}",
                searchResult.matches().size(), memoryText);
        return "searchLongTermMemory 结果：查询成功。检索到的相关长期记忆如下：" + memoryText;
    }

    @Tool("""
            当用户提到确定的个人事实，如名字、生日、明确的固定喜好、家庭信息时优先使用。
            请将其转化为“类别或别名 + 具体值 + 简洁描述”。
            工具返回结果后，请直接基于结果回答用户，不要继续重复保存。
            """)
    public String saveLongTermMemory(@P("事实的分类或别名，如：生日、忌口、梦想") String aliases,
                                     @P("具体的内容值") String configValue,
                                     @P("对事实的简洁描述") String description) {
        log.info("LLM开始调用 saveLongTermMemory ，aliases：{}，configValue：{}，description：{}",
                aliases, configValue, description);
        try {
            UserPreferenceEntity entity = new UserPreferenceEntity();
            entity.setAliases(aliases);
            entity.setConfigValue(configValue);
            entity.setDescription(description);
            userRepo.save(entity);
            log.info("LLM调用 saveLongTermMemory 成功。aliases: {} 写入长期记忆，configValue: {}，description: {}。",
                    aliases, configValue, description);
            return "saveLongTermMemory 结果：保存成功。已写入长期记忆，aliases:["
                    + aliases + "]，configValue:[" + configValue + "]，description:[" + description + "]。";
        } catch (Exception e) {
            log.error("LLM调用 saveLongTermMemory 失败，参数aliases：{}，configValue：{}，description：{}",
                    aliases, configValue, description, e);
            return "saveLongTermMemory 结果：保存失败，长期记忆未能写入。";
        }
    }

    @Tool("""
            记录用户重要的感性记忆或长篇故事。
            当信息比较零碎、带有情绪色彩或属于阶段性总结时使用。
            工具返回结果后，请直接基于结果回答用户，不要继续重复保存。
            """)
    public String saveFragmentedMemory(@P("压缩后的记忆片段，建议包含人物、事件和情感。") String content) {
        log.info("LLM开始调用 saveFragmentedMemory ，内容：{}", content);

        TextSegment segment = TextSegment.from(content);
        vectorStore.add(embeddingModel.embed(segment).content(), segment);

        log.info("LLM调用 saveFragmentedMemory 成功。记忆片段 {} 已写入向量记忆库。", content);
        return "saveFragmentedMemory 结果：保存成功。该记忆片段已写入向量记忆库。";
    }
}
