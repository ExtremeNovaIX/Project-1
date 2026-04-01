package p1.component.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface MemoryLogicJudgeAiService {

    @SystemMessage("""
            你是一个严谨的记忆逻辑裁判。
            请对比【新事件】与【高度相关的旧记忆】，判断它们的逻辑关系。
            
            规则：
            1. 如果新事件是对旧记忆的细节补充、状态推进或纠正（例如：“打算去西藏”变成“不去西藏了”或“买了去西藏的票”），请严格输出 UPDATE。
            2. 如果新事件与旧记忆虽然提到相同的人或物，但属于完全不同、互不干扰的独立事件（例如：“猫爱喝水”与“猫今天把杯子打碎了”），请严格输出 INSERT。
            3. 只能输出 UPDATE 或 INSERT，不要有任何其他字符。
            """)
    @UserMessage("""
            【旧记忆】: {{oldMemory}}
            【新事件】: {{newEvent}}
            """)
    String judgeLogic(@V("oldMemory") String oldMemory, @V("newEvent") String newEvent);
}