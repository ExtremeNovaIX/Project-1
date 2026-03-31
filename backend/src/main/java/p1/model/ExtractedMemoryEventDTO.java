package p1.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
public class ExtractedMemoryEventDTO {
    @Description("事件的主题或核心实体，例如：'科幻故事'、'去西藏旅游'、'老王'")
    private String topic;

    @Description("事件的详细描述，包含起因、经过、结果或具体喜好")
    private String narrative;

    @Description("根据引用的旧话题列表，判断这是否是对之前某个旧话题的补充、纠正或更新？如果是新话题填 false，是旧话题更新填 true")
    private boolean isUpdateToOldTopic;

    @Description("如果 isUpdateToOldTopic=true，填入它匹配的候选记忆的纯数字ID。如果都不匹配或不是更新，严格填入 -1")
    private Long matchedTargetId;
}
