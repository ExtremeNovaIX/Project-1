package p1.component.ai.assistant;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;
import p1.component.ai.memory.SummaryCacheManager;
import p1.model.ChatLogEntity;
import p1.repo.db.ChatLogRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FrontendChatRequestAugmentorTest {

    @Test
    void shouldInsertDynamicMemoryUserMessageBeforeLastUserMessage() {
        SummaryCacheManager summaryCacheManager = mock(SummaryCacheManager.class);
        ChatLogRepository chatLogRepository = mock(ChatLogRepository.class);
        FrontendChatRequestAugmentor augmentor = new FrontendChatRequestAugmentor(chatLogRepository);

        when(summaryCacheManager.getSummary("session-1"))
                .thenReturn("以下是之前的对话摘要：\n摘要 1: 提到过星际旅行");

        ChatLogEntity latestUserMessage = new ChatLogEntity();
        latestUserMessage.setCreatedAt(LocalDateTime.of(2026, 4, 24, 11, 0, 0));
        ChatLogEntity previousUserMessage = new ChatLogEntity();
        previousUserMessage.setTime(LocalDateTime.of(2026, 4, 24, 10, 30, 0));
        when(chatLogRepository.findTop2BySessionIdAndRoleOrderByCreatedAtDesc("session-1", "USER"))
                .thenReturn(List.of(latestUserMessage, previousUserMessage));

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("我们继续聊昨天那个剧情")))
                .build();

        ChatRequest augmented = augmentor.augment(request, "session-1");
        List<ChatMessage> messages = augmented.messages();

        assertEquals(2, messages.size());
        UserMessage dynamicMemory = assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals("dynamic_memory", dynamicMemory.name());
        assertTrue(dynamicMemory.singleText().startsWith("<dynamic_memory>"));
        assertTrue(dynamicMemory.singleText().contains("<summary>"));
        assertTrue(dynamicMemory.singleText().contains("摘要 1: 提到过星际旅行"));
        assertTrue(dynamicMemory.singleText().contains("上次用户发消息时间：2026-04-24 10:30:00"));
        assertFalse(dynamicMemory.singleText().contains("<user_query>"));

        UserMessage userMessage = assertInstanceOf(UserMessage.class, messages.get(1));
        assertEquals("我们继续聊昨天那个剧情", userMessage.singleText());
    }
}
