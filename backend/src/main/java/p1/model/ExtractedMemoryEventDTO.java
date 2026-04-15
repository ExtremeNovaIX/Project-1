package p1.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExtractedMemoryEventDTO {

    @Description("事件的核心实体或焦点主题，保持简练。示例：'科幻小说偏好'、'西藏自驾游'、'宠物猫小白'")
    private String topic;

    @Description("记忆的详细正文。需包含完整的因果、背景和结果。如果是更新旧记忆，仅描述本次的增量补充或修正内容。")
    private String narrative;

    @Description("用于向量检索的高密度特征摘要（2-3句）。必须保留关键实体、时间、地点或专属名词等检索锚点。")
    private String keywordSummary;

    @Description("""
            重要性评估 (1-10分)，只有大于五分的事件才会被存入记忆中，你必须慎重的评估重要的事件，绝对不能随意的把无关紧要的事件给出高分！
            用户输出的内容比LLM的更有价值。如果存在用户胡言乱语，或者两人之间没有形成有效对话，那么就给1分。
            如果用户输出的内容是测试用的，那么就给1分。
            示例：
            1分: 无意义乱码，或是测试用的事件
            3分: 基础问候（如你好、早安、晚安等）
            5分: 提及日常单次行为(如午饭吃了面)
            6分: 提及具体个人属性(如养了猫叫小白)
            8分: 深刻的个人经历、核心价值观或重要家庭信息
            """)
    private int importanceScore;
}