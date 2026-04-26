package p1.component.agent.memory;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

public interface FactEvaluatorAiService {

    @SystemMessage("""
            角色与任务：你是一个高精度的结构化数据处理与实体识别系统。请先对输入事件进行全局因果推理，随后提取高精度标签与核心摘要，最后严格按系统预设的 JSON 格式输出。
            
            绝对格式要求：
            你必须直接输出纯正的 JSON 文本格式。
            绝对禁止输出任何 JSON 范围之外的解释说明或思考过程。
            绝对禁止使用任何 Markdown 语法和代码块围栏。
            
            执行逻辑与处理规则（严格按顺序）：
            第一：前置全局推演。你必须最先在 causalityAnalysis 字段中，对输入数据进行全局梳理，写明本批次事件的整体前因后果与核心发展脉络。
            
            第二：高精度标签（Tags）提取。基于全局推演，在 tags 列表中提取绝对具体、无歧义的不可变锚点。
            tags 必须是“重要事件组”的高精度组级标签，用于精准定位和召回记忆。必须且仅限包含以下几类核心锚点：
            1. 核心实体：具体人名、关键专有道具、明确地点或特殊组织。
            2. 核心机制与情境：推动事件发展的特定规则、比赛、赌约、约定、报复目标、冲突主题、任务目标、仪式、阵法名称，以及决定事件走向的关键名词或短语。
            3. 专属命名：特殊事件名称或专有名词。
            提取约束：禁止提取孤立动词、形容词、泛指代词、情绪状态或宽泛概括词；但允许提取已经形成明确事件语义的名词性短语，例如“赌约”“报复计划”“试炼排名”；必须还原真实全名以消除歧义；且提取出的 tag 必须是原文段落中出现过的词语。
            提取规范：tag必须是原子单位，不能出现组合词。错误示例：“特里蒙地平弧光计划”。正确示例：“特里蒙“、”地平弧光计划”。
            
            第三：事件列表完整提取。在 events 列表中，必须保持输入事件的原始顺序，绝不能增删。对每个事件：topic 字段必须一字不差地复制输入内容的 topic；keywordSummary 需用 1 到 2 句话精确概括该事件的主体和结果，绝不编造输入中不存在的新事实。
            
            第四：最终全局总结。在完成了上述所有梳理和提取后，在最后的 summary 字段输出 2 到 4 句话的高度浓缩总结，确保关键事实无遗漏。
            """)
    @UserMessage("""
            以下是已经提取并完成初评的最终重要事件组：
            {{extractedEvents}}
            """)
    FactExtractionService.FactSummaryDTO evaluateAndSummarizeFacts(@V("extractedEvents") List<FactExtractionService.ExtractedFactEventDTO> extractedEvents);
}
