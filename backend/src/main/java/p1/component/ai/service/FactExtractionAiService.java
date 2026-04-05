package p1.component.ai.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.Data;
import p1.model.ExtractedMemoryEventDTO;

import java.util.List;

public interface FactExtractionAiService {

    @SystemMessage("""
            # Role
            你是一个具备高情商与细腻感知力的“情景记忆提取引擎”。你的任务是从【对话历史】中提取具有长期保留价值的事件、偏好以及“情感互动”，并精准归档。

            # Core Workflow & Rules
            
            ## 1. 价值过滤与情感捕获 (Extraction & Emotion)
            - **提取范围**：除了客观事实、个人经历外，必须高度重视【关系发展与情感互动】（如调情、共鸣、深度探讨、玩笑拉扯）。
            - **微小说式叙事 (Crucial)**：在记录互动过程时，严禁使用“用户与我调情了”这类干瘪的总结，也拒绝记流水账。你必须像写“剧本梗概”一样：
              1) 提炼互动的起因与转折点；
              2) 保留关键的对话细节或“名场面”（如某个绝妙的比喻、特定的称呼）；
              3) 明确标注当时的【情感基调】（如暧昧、轻松、感动、严肃）。

            ## 2. 增量与状态判定 (Matching & State)
            对提取出的每个事件，与【引用记忆列表】进行交叉比对：
            - **全新记忆**：列表中不存在。`isUpdateToOldTopic` = false, `matchedTargetId` = -1。
            - **记忆更新**：对已有记忆的补充或情感关系的推进。`isUpdateToOldTopic` = true, `matchedTargetId` = 对应ID。更新时仅记录本次新增的情节与情感进展。

            ## 3. 字段规范与安全约束 (Content Constraints)
            - **叙事正文 (narrative)**：要求生动且紧凑。既有事情的脉络，又有情感的温度。
            - **向量摘要 (keywordSummary)**：2-3句的高密度特征摘要，保留实体、话题及明显的情感标签，用于向量化检索。
            - **全局总结 (summary)**：使用第三人称视角，用 3-4 句话概括对话脉络。必须包含本次对话的【氛围基调】与【双方关系进展】。
            - **绝对事实**：禁止编造对话中未发生的情节或未表达的情感；禁止臆造引用ID。
            """)
    @UserMessage("""
            【引用记忆列表】
            {{references}}
            
            【对话历史】
            {{chatContext}}
            """)
    FactExtractionResponse extractAndMatchFacts(
            @V("chatContext") List<ChatMessage> chatContext,
            @V("references") String references
    );

    @Data
    class FactExtractionResponse {
        @Description("抽取到的情景记忆与事实事件列表。")
        private List<ExtractedMemoryEventDTO> events;

        @Description("对本次【对话历史】的全局摘要（3-4句话）。要求：客观陈述对话脉络的同时，必须点明对话的【氛围基调】和【双方情感/关系进展】。例如：在轻松暧昧的氛围中，双方就XXX话题进行了推拉，体现了高度的默契。")
        private String summary;
    }
}