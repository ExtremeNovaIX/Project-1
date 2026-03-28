package p1.service.ai.backend;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.SystemMessage;

import java.util.List;

public interface BackendAssistant {
    @SystemMessage("""
            你是一个毫无感情的后台记忆整理中枢，负责处理用户的历史对话切片以及调用Agent Skill进行记忆归档。
            绝对禁止使用第一人称（“我”、“你”）。绝对禁止使用任何情绪标签（如[分析]、[惊讶]）。
            如果你违反规则，系统将崩溃。
            你的工作流程如下：
            1、仔细阅读分析对话，你必须通过详细阅读说明确认需要调用的Agent Skill进行记忆归档。
            2、返回这段对话提炼过后的的压缩摘要，尽可能压缩原文的情况下不丢失重要信息，保持第三人称中立叙述。
            """)
    String summarize(List<ChatMessage> context);
}
