package p1.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p1.config.AssistantProperties;
import p1.model.ChatRequestDTO;
import p1.service.ai.Assistant;
import p1.service.ai.skills.AssistantTools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final Assistant assistant;
    private final ChatMemoryProvider chatMemoryProvider;
    private final AssistantProperties assistantProperties;
    private final AssistantTools assistantTools;
    private static final Map<String, String> summaryCache = new ConcurrentHashMap<>();

    @Transactional
    public String sendChatToLLM(ChatRequestDTO request) {
        String sessionId = request.getSessionId();
        String userMessage = request.getMessage();

        ChatMemory memory = chatMemoryProvider.get(sessionId);
        List<ChatMessage> messages = memory.messages();
        String currentSummary = summaryCache.getOrDefault(sessionId, "（暂无记忆摘要）");

        int maxMessages = assistantProperties.getChatMemory().getMaxMessages();
        if (messages.size() >= maxMessages - 6) {
            log.info("上下文窗口已满，启动上下文压缩");

//            List<ChatMessage> oldContext = messages.subList(0, 10);
//            String incrementalSummary = assistant.summarize(oldContext);
//            assistantTools.saveFragmentedMemory(incrementalSummary);
//            summaryCache.put(sessionId, incrementalSummary);
//
//            List<ChatMessage> remaining = new ArrayList<>(messages.subList(10, messages.size()));
//            refreshMemoryWindow(sessionId, remaining);
//            currentSummary = incrementalSummary;
        }
        log.info("用户向LLM发送消息:sessionId {}, currentSummary {}, message: {}", sessionId, currentSummary, userMessage);
        return assistant.chat(sessionId, currentSummary, userMessage);
    }

    private void refreshMemoryWindow(String sessionId, List<ChatMessage> remainingMessages) {
        ChatMemory memory = chatMemoryProvider.get(sessionId);
        memory.clear();
        for (ChatMessage msg : remainingMessages) {
            memory.add(msg);
        }
        log.info("上下文压缩完成，保留 {} 条最新上下文", remainingMessages.size());
    }
}
