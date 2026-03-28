package p1.service.ai.skills;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p1.model.MemoryArchiveEntity;
import p1.model.UserPreferenceEntity;
import p1.repo.MemoryArchiveRepository;
import p1.repo.UserPreferenceRepository;

@Component
@AllArgsConstructor
@Slf4j
public class MemorySaveTools {

    private final UserPreferenceRepository userRepo;
    private final EmbeddingStore<TextSegment> vectorStore;
    private final EmbeddingModel embeddingModel;
    private final MemoryArchiveRepository archiveRepo;

    @Tool("""
            当用户讲述了一个完整的故事、长篇经历、重要设定或复杂事件时调用此工具。
            你必须对内容进行“双轨压缩”，都以第三人称客观叙述。
            """)
    public String compressMemory(
            @P("分类标签，例如 [虚构故事]、[现实生活]、[游戏探讨]。") String category,
            @P("极度压缩的一句话事情梗概，后续将会用于向量检索。") String keywordSummary,
            @P("不丢失重要信息的压缩总结，保留核心细节和起承转合并且去掉无用的信息") String detailedSummary) {

        log.info("LLM开始调用 compressMemory 进行记忆压缩。分类标签:{}, 记忆梗概:{}", category, keywordSummary);

        try {
            // 存入H2数据库，获取ID
            MemoryArchiveEntity entity = new MemoryArchiveEntity();
            entity.setCategory(category);
            entity.setKeywordSummary(keywordSummary);
            entity.setDetailedSummary(detailedSummary);
            entity = archiveRepo.save(entity);

            // 将标签和ID存入Lucene元数据，建立DB和Lucene的关联关系
            Metadata metadata = new Metadata();
            metadata.put("db_id", entity.getId().toString());
            metadata.put("category", category);
            TextSegment segment = TextSegment.from(keywordSummary, metadata);
            vectorStore.add(embeddingModel.embed(segment).content(), segment);

            log.info("LLM调用 compressMemory 成功。Lucene-DB关系已建立，DB主键: {}", entity.getId());
            return "复杂记忆归档成功。";
        } catch (Exception e) {
            log.error("LLM调用 compressMemory 失败，记忆压缩失败", e);
            return "归档失败，内部存储异常。";
        }
    }

    @Tool("""
            当用户提到确定的个人事实，如名字、生日、明确的固定喜好、家庭信息时优先使用。
            """)
    public String saveLongTermMemory(@P("事实的类别或别名，如：生日、忌口、梦想") String aliases,
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
            """)
    public String saveFragmentedMemory(@P("压缩后的记忆片段，建议包含人物、事件和情感。") String content) {
        log.info("LLM开始调用 saveFragmentedMemory ，内容：{}", content);

        TextSegment segment = TextSegment.from(content);
        vectorStore.add(embeddingModel.embed(segment).content(), segment);

        log.info("LLM调用 saveFragmentedMemory 成功。记忆片段 {} 已写入向量记忆库。", content);
        return "saveFragmentedMemory 结果：保存成功。该记忆片段已写入向量记忆库。";
    }
}
