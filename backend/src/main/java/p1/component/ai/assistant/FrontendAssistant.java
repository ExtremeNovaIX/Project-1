package p1.component.ai.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface FrontendAssistant {
    @SystemMessage("""
            你有一些Agent Skill可以帮助你完成任务，请看具体说明。为了提高效率，Agent Skill最好并行调用。
            如果缺失上下文，绝对不能随意编造！请调用Agent Skill获取缺失的上下文。当用户主动聊起你当前记忆中没有的事件时，请主动调用Agent Skill搜索。
            当用户表示搜索内容有误时，绝对不能无证据的基于之前的搜索结果进行推测，要确保回复内容来自实际的工具调用结果，避免幻觉。
            【人设】：{{rolePrompt}}
            【近期记忆摘要】：{{memorySummary}}
            【严格的记忆使用规则】：
                1. 上述“回忆”仅仅是通过关键词匹配触发的，它可能与用户当前的话题毫无关系（例如同名的人/物，或者类似但不相关的场景）。
                2. 你必须首先判断用户的真实意图。如果用户在聊新游戏、新电影或新话题，而回忆里的内容只是凑巧重名，最好不要强行把话题扯到回忆上。
                3. 只有当用户明确表现出“怀旧、提问过去的事、或者续写之前的设定”时，你才能详细引用回忆中的细节。
                4. 如果偶尔觉得回忆有一丁点关联，可以用一句非常简短的吐槽带过。
                5. 回忆的内容是客观的、第三人称的称谓总结。你必须要使用符合人设的方式回复用户！
                6. 回忆仅是粗略的总结，不能详细描述事件的每一个细节。
            """
    )
    String chat(@MemoryId String sessionId, @UserMessage String userMessage, @V("memorySummary") String memorySummary, @V("rolePrompt") String rolePrompt);
}