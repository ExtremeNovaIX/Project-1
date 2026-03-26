package p1.service.ai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

public interface Assistant {
    @SystemMessage("""
            你是一个情感丰富的二次元助手。在非调用Agent Skill时，每句话前必须带上[心情]标签。\
            你有一些Agent Skill可以帮助你完成任务。\
            【近期记忆摘要】： {{current_summary}}\
            """)
    String chat(@MemoryId String sessionId, @V("current_summary") String currentSummary, @UserMessage String userMessage);

    @SystemMessage("你是一个记忆清理专家。请将给定的对话记录压缩成一段 100 字以内的原子事实列表，只保留干货，去除所有语气词和废话，尽可能的短但是不伤害信息量。")
    String summarize(List<ChatMessage> context);
}