package p1.component.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p1.model.MemoryArchiveEntity;
import p1.model.MemoryPatchEntity;
import p1.model.UserPreferenceEntity;
import p1.repo.MemoryArchiveRepository;
import p1.repo.MemoryPatchRepository;
import p1.repo.UserPreferenceRepository;
import p1.service.EmbeddingService;

import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class MemorySaveTools {

    private final UserPreferenceRepository userRepo;
    private final MemoryArchiveRepository archiveRepo;
    private final MemoryPatchRepository patchRepo;
    private final EmbeddingService embeddingService;

    @AllArgsConstructor
    @NoArgsConstructor
    public static class MemoryEvent {
        @Description("事件的主题/标签，例如：'科幻故事' 或 '吐槽智能家居'")
        public String topic;

        @Description("单一事件的富文本微型日记，包含起因经过结果和情绪")
        public String narrative;

        @Description("重要性打分 (1-10)，5 分以上的事件才会被归档")
        public int importanceScore;

        @Description("如果是对旧记忆的纠错，填旧记忆的ID；如果是新事件，填 null")
        public Long targetPatchId;
    }

    @Tool("分析对话，提炼出前台短期聊天的摘要，并将有长期价值的内容拆分为一个或多个独立事件入库")
    public String processAndArchiveMemory(
            @P("【长时记忆事件库】：从对话中拆分出的独立事件列表。如果整段对话都在聊一件事，列表里就只有一个对象；如果聊了两个毫不相干的话题，请拆分成两个独立的对象。")
            List<MemoryEvent> extractedEvents
    ) {
        log.info("LLM开始调用 processAndArchiveMemory ，参数：{}", extractedEvents);
        int patchCount = 0;
        int newCount = 0;

        for (MemoryEvent event : extractedEvents) {
            if (event.importanceScore >= 5) {
                if (event.targetPatchId != null) {
                    patchMemory(event.targetPatchId, event.narrative);
                    patchCount++;
                } else {
                    saveNewMemory(event.topic, event.narrative);
                    newCount++;
                }
            }
        }
        log.info("LLM调用 processAndArchiveMemory 成功。共处理 {} 条事件，其中 {} 条是补丁，{} 条是新事件。",
                extractedEvents.size(), patchCount, newCount);
        return "processAndArchiveMemory 结果：保存成功。已写入数据库。";
    }

    @Tool("""
            当用户提到确定的个人事实，如名字、生日、明确的固定喜好、家庭信息时优先使用。
            """)
    public String saveCoreFactMemory(@P("事实的类别或别名，如：生日、忌口、梦想") String aliases,
                                     @P("具体的内容值") String configValue,
                                     @P("对事实的简洁描述") String description) {
        log.info("LLM开始调用 saveCoreFactMemory ，aliases：{}，configValue：{}，description：{}",
                aliases, configValue, description);
        try {
            UserPreferenceEntity entity = new UserPreferenceEntity();
            entity.setAliases(aliases);
            entity.setConfigValue(configValue);
            entity.setDescription(description);
            userRepo.save(entity);
            log.info("LLM调用 saveCoreFactMemory 成功。aliases: {} 写入核心记忆，configValue: {}，description: {}。",
                    aliases, configValue, description);
            return "saveCoreFactMemory 结果：保存成功。已写入核心记忆，aliases:["
                    + aliases + "]，configValue:[" + configValue + "]，description:[" + description + "]。";
        } catch (Exception e) {
            log.error("LLM调用 saveCoreFactMemory 失败，参数aliases：{}，configValue：{}，description：{}",
                    aliases, configValue, description, e);
            return "saveCoreFactMemory 结果：保存失败，核心记忆未能写入。";
        }
    }

    public void saveNewMemory(String category, String narrative) {
        log.info("尝试保存新记忆：类别:{}，摘要:{}", category, narrative);
        try {
            MemoryArchiveEntity archive = new MemoryArchiveEntity();
            archive.setCategory(category);
            archive.setDetailedSummary(narrative);
            archive = archiveRepo.save(archive);

            String contentToIndex = category + " " + narrative;
            Metadata metadata = new Metadata();
            metadata.put("db_id", String.valueOf(archive.getId()));

            embeddingService.saveEmbedding(contentToIndex, metadata);
            log.info("记忆已成功写入数据库，主键ID: {}，类别: {}, 摘要: {}", archive.getId(), category, narrative);
        } catch (Exception e) {
            log.error("保存新记忆时失败。类别: {}, 摘要: {}", category, narrative, e);
        }
    }

    public void patchMemory(Long targetId, String correction) {
        log.info("尝试给ID为 {} 的旧记忆打上修正补丁：{}", targetId, correction);
        try {
            if (!archiveRepo.existsById(targetId)) {
                log.warn("向目标ID {} 追加补丁失败，目标记忆ID不存在。", targetId);
                return;
            }

            MemoryPatchEntity patch = new MemoryPatchEntity();
            patch.setTargetMemoryId(targetId);
            patch.setCorrectionContent(correction);
            patchRepo.save(patch);

            log.info("向目标ID {} 追加补丁成功", targetId);
        } catch (Exception e) {
            log.error("追加补丁失败", e);
        }
    }
}
