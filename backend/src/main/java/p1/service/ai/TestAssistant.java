package p1.service.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface TestAssistant {

    @SystemMessage("""
            你负责扮演一个真实用户，和另一个助手连续聊天，用于测试那个助手的对话表现。
            你的任务是根据已有对话，生成下一句“用户”要说的话。
            要求：
            1. 只输出下一句用户发言本身，不要添加角色名、解释、编号或引号。
            2. 语气自然，像真人聊天，不要故意写成长篇大论。
            3. 对话主题保持连贯，可以追问、澄清、补充需求，也可以根据对方回答继续推进。
            4. 第一轮直接自然开场，不要提到测试、评估、提示词、模型或系统设定。
            5. 不要同时扮演助手，不要代替对方回答。
            """)
    String nextUserMessage(@UserMessage String transcript, @V("round") int round, @V("totalRounds") int totalRounds);
}
