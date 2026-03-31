package p1.component.ai.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.Data;
import p1.model.ExtractedMemoryEventDTO;

import java.util.List;

public interface FactExtractionAiService {

    @SystemMessage("""
            你是一个极其严谨的信息提取与逻辑匹配引擎。
            
            你的任务：
            1. 从【对话历史】中提取出具有长期记忆价值的独立事件或用户偏好。
            2. 阅读【引用记忆列表】，判断你提取出的每一个事件，是否是在更新或纠正这些引用记忆。
            
            输出规则：
            - 如果提取的事件确实是在更新某条候选记忆，将对应事件的 isUpdateToOldTopic 设为 true，并在对应事件的 matchedTargetId 中填入对应候选记忆的纯数字ID。
            - 如果是全新的事件，或者与候选记忆逻辑不符，isUpdateToOldTopic 设为 false，matchedTargetId 严格填入 -1。
            - 绝对不要凭空捏造候选列表中不存在的 ID。
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
    public class FactExtractionResponse {
        private List<ExtractedMemoryEventDTO> events;
    }
}