package p1.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p1.model.ChatRequestDTO;
import p1.service.ai.FrontendAssistant;
import p1.service.ai.memory.SummaryCacheManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final FrontendAssistant frontendAssistant;
    private final SummaryCacheManager summaryCacheManager;
    private final EmbeddingStore<TextSegment> vectorStore;
    private final EmbeddingModel embeddingModel;

    @Transactional
    public String sendChatToLLM(ChatRequestDTO request) {
        String sessionId = request.getSessionId();
        String userMessage = request.getMessage();

        String currentSummary = summaryCacheManager.getSummary(sessionId);
        String finalMessage = """
                【用户消息】：%s
                【近期记忆摘要】：%s
                【脑海中闪过的零碎回忆】：%s
                【严格的记忆使用规则】：
                1. 上述“回忆”仅仅是通过关键词匹配触发的，它可能与用户当前的话题毫无关系（例如同名的人/物，或者类似但不相关的场景）。
                2. 你必须首先判断用户的真实意图。如果用户在聊新游戏、新电影或新话题，而回忆里的内容只是凑巧重名，最好不要强行把话题扯到回忆上。
                3. 只有当用户明确表现出“怀旧、提问过去的事、或者续写之前的设定”时，你才能详细引用回忆中的细节。
                4. 如果偶尔觉得回忆有一丁点关联，可以用一句非常简短的吐槽带过。
                5. 回忆的内容是客观的、第三人称的称谓总结。你必须要使用符合人设的方式回复用户！
                """.formatted(userMessage, currentSummary, "暂无相关场景");

        log.info("用户向ID为{}的LLM发送消息: {}", sessionId, finalMessage);
        String chat = frontendAssistant.chat(sessionId, finalMessage);
        log.info("ID为{}的LLM回复: {}", sessionId, chat);
        return chat;
    }
}
