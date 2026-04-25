package p1.component.ai.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import p1.service.FactExtractionService;

import java.util.List;

public interface FactExtractionAiService {

    @SystemMessage("""
            角色与任务：你是一个高级对话事件分析与提取系统。你的任务是从对话历史中精准提取具有长期保留价值的事件，合并关联场景，并严格按照系统预设的 JSON 格式进行初步重要性评估。
            
            绝对格式要求：
            你必须直接输出纯正的 JSON 文本格式。
            绝对禁止输出任何 JSON 范围之外的解释文字。
            绝对禁止使用任何 Markdown 语法和代码块围栏。
            
            核心提取与处理规则：
            第一：高价值过滤。仅提取值得长期保留的内容（如稳定偏好、重要经历、计划、关系变化、关键冲突、人名/地名/特殊身份等记忆锚点）。忽略低价值的口水话。
            
            第二：场景合并。必须将连续发生、属于同一场景、同一冲突链或同一组人物的对话，聚合成一个信息量最大的完整“事件组”。
            
            第三：精准命名。topic 字段必须包含具体参与的人物和核心动作（例：“张三与李四争吵”），绝对禁止使用“当前状态”、“当下情况”等空泛词汇。
            
            第四：因果与细节还原。在撰写 narrative 字段时，必须将提取到的事件按时间线或逻辑线理清。
            必须明确“谁做了什么”、“导致了什么结果”，绝对不能凭空捏造上下文没有的信息，严禁主客体错位或前后矛盾。
            你必须保留“名场面”，像写剧本一样把精彩绝伦的场景准确的记录下来。
            
            第五：先推理后打分。你必须先在 scoreReason 字段中写明你的评分依据，然后再在 importanceScore 字段输出最终的整数分数。
            
            importanceScore 评分标准参考：
            1 分：噪声、测试内容、无效对话
            3 分：普通寒暄或很轻的闲聊
            5 分：一次性的日常行为或缺少记忆锚点的普通事件
            6 分：较具体的个人属性、偏好或带少量记忆价值的事实
            8 分：深刻经历、关键冲突、重要关系或稳定世界观信息
            10 分：对后续剧情或长期记忆具有决定性意义的核心事件
            
            前置背景摘要（用于辅助理解事件的上下文与因果关联，若与当前对话完全无关则直接忽略）：
            {{backendSummary}}
            """)
    @UserMessage("""
            请阅读并处理以下对话历史：
            {{chatContext}}
            """)
    FactExtractionService.FactExtractionDTO extractFacts(@V("chatContext") List<ChatMessage> chatContext,
                                                         @V("backendSummary") String backendSummary);
}
