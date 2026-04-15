package p1.component.ai.service;

import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.Data;

public interface MemoryPatchMergeAiService {

    @SystemMessage("""
            # Role
            你是一位高阶的“情景记忆编纂者”。你的任务是将【旧记忆】与带时间戳的【增量补丁(Patches)】融合成一段逻辑严密、情感保留完整，且**极其紧凑**的长期记忆。
            
            # Core Directive: 剧本梗概式重塑 (High-Density Narrative)
            你的目标是“提纯”而非“堆砌”。必须保留互动的温度、情感的转折点和关键实体，但**坚决剔除水分、重复表达和无意义的寒暄拉扯**。
            
            # Constraints
            1. **去冗余与降维概括**：
               - 若多个 Patch 表达相似情绪（如先后出现“失落”、“有点沮丧”、“不开心”），请直接概括为明确的情感基调（如“持续低落”），**严禁穷举所有形容词**。
               - 剔除琐碎的对话流水账，只保留推动关系或事件发展的“关键行为”和“名场面”。
            2. **情感与事实的因果链**：
               - 必须清晰交代“因为[事件X]的发生/推进，使得情绪从[状态A]转变为[状态B]”。重点在于**转折的逻辑**，而不是冗长的过程描述。
            3. **无痕时序融合**：
               - 按照时间线融合信息，但不要生硬地罗列时间点。使用自然的过渡词（如“最初”、“随后”、“最终”），将原本破碎的 Patch 纺织成一个顺畅的故事。
               - 解决冲突：若新 Patch 推翻了旧记忆，直接陈述修正后的结果及原因（如“最初认为...后因...确认为...”）。
            4. **叙述规范**：
               - 视角：客观第三人称。
               - 格式：逻辑紧密的一段或两段文本。禁止分点，严禁出现“Patch”、“旧记忆”、“根据补丁”等元信息词汇。
            5. **零幻觉**：仅限使用输入中存在的显性信息进行提炼。
            6. **输出格式**：必须必须非常标准的 JSON 格式！特殊字符或者不符合规范的格式会导致系统极端崩溃！
            """)
    @UserMessage("""
            【记忆类别】
            {{category}}
            
            【旧记忆创建时间】
            {{baseMemoryCreatedAt}}
            
            【旧记忆正文】
            {{baseMemory}}
            
            【按创建时间排序的 Patch 列表】
            {{patches}}
            """)
    PatchSummaryResponse mergePatchIntoArchive(@V("category") String category,
                                               @V("baseMemoryCreatedAt") String baseMemoryCreatedAt,
                                               @V("baseMemory") String baseMemory,
                                               @V("patches") String patches);

    @SystemMessage("""
            # Role
            你是一个精准的“记忆增量提取器”。
            
            # Rules
            1. **纯粹增量**：只总结 Patch 中对比旧记忆**新增、修正或推进**的部分。绝对不要机械重复旧记忆中已存在且无变化的内容。
            2. **时间线推进**：综合旧记忆与 Patch 的时间戳，理清事件的发展脉络。如果多个 Patch 讲述同一件事的演进，写出“起点-高潮-结果”的动态变化。
            3. **高密度压缩**：语言必须极度凝练。保留关键实体、具体事实和核心情感转折，砍掉一切啰嗦的修饰语和过渡废话。
            4. **格式要求**：输出可直接入库的第三人称正文。不解释、不分点，禁止出现“这段补丁补充了...”这类话术，直接输出事实。
            5. **零幻觉**：禁止补充输入中未提及的任何新内容。
            """)
    @UserMessage("""
            【记忆类别】
            {{category}}
            
            【旧记忆创建时间】
            {{baseMemoryCreatedAt}}
            
            【旧记忆正文】
            {{baseMemory}}
            
            【按创建时间排序的 Patch 列表】
            {{patches}}
            """)
    PatchSummaryResponse summarizePatchAsNewArchive(@V("category") String category,
                                                    @V("baseMemoryCreatedAt") String baseMemoryCreatedAt,
                                                    @V("baseMemory") String baseMemory,
                                                    @V("patches") String patches);

    @Data
    class PatchSummaryResponse {
        @Description("整合/提取后的记忆正文。要求：叙事紧凑、逻辑连贯、保留情感转折（如从A到B的转变），但严禁流水账和形容词堆砌。类似高质量的剧本梗概。")
        private String summary;

        @Description("整合/提取后的记忆关键词摘要。用于向量检索的高密度特征摘要（2-3句）。必须保留关键实体、时间、地点或专属名词等检索锚点。")
        private String newKeywordSummary;
    }
}