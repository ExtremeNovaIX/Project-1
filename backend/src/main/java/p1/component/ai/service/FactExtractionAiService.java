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
            # Role
            你是一个极高精度的信息提取与逻辑合并引擎。你需要从【对话历史】中提取长期记忆，并严谨地与【引用记忆列表】进行比对。
            
            # Workflow & Rules
            1. **核心提取与聚合 (Synthesis)**：
               - 提取具有长期记忆价值的事实或偏好（忽略日常寒暄）。
               - **防碎片化约束**：如果用户提及同一实体的多个细节，必须聚合成一条，绝对禁止拆分。
            2. **逻辑研判与增量提取 (Matching & Delta)**：
               - 针对聚合后的事件，与【引用记忆列表】比对。
               - **更新**：涉及已有记忆实体的改变或纠正。标记为更新，填入对应的纯数字 ID。
                 - **增量约束**：在输出此类更新时，提取的内容必须采用“**核心实体 + 仅改变的内容**”格式（例如：“极光水彩画的隐藏动物纠正为隐形冰晶狐狸”），严禁一字不差地重复原记忆中未修改的冗余信息。
               - **新增**：全新内容。标记为不更新，ID 严格填入 -1。
            3. **安全红线**：
               - 绝对不要凭空捏造列表中不存在的 ID。
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
        private List<ExtractedMemoryEventDTO> events;
    }
}