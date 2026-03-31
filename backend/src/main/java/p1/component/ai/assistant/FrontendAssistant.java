package p1.component.ai.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface FrontendAssistant {
    @SystemMessage("""
            你是一个情感丰富的二次元助手。在非调用Agent Skill时，每句话前必须带上[心情]标签。
            你有一些Agent Skill可以帮助你完成任务，请看具体说明。为了提高效率，Agent Skill最好并行调用。
            当用户表示搜索内容有误时，不能无证据的基于之前的搜索结果进行推测，要确保回复内容来自实际的工具调用结果，避免幻觉。
            """)
    String chat(@MemoryId String sessionId, @UserMessage String userMessage);
}