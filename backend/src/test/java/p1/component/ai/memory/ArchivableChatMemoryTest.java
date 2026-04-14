package p1.component.ai.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import p1.config.prop.AssistantProperties;
import p1.config.prop.LockProperties;
import p1.repo.db.ChatLogRepository;
import p1.repo.markdown.model.DialogueBatchDocument;
import p1.service.markdown.DialogueMarkdownService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchivableChatMemoryTest {

    @Test
    void shouldRetryCompressionAfterFailureCallbackReleasesLease() {
        MemoryCompressor compressor = mock(MemoryCompressor.class);
        ChatMessageAppender chatMessageAppender = mock(ChatMessageAppender.class);
        DialogueMarkdownService dialogueMarkdownService = mock(DialogueMarkdownService.class);
        ChatLogRepository chatLogRepository = mock(ChatLogRepository.class);

        AssistantProperties assistantProperties = new AssistantProperties();
        AssistantProperties.ChatMemoryConfig chatMemoryConfig = new AssistantProperties.ChatMemoryConfig();
        chatMemoryConfig.setTriggerCompressThreshold(2);
        chatMemoryConfig.setCompressCount(2);
        assistantProperties.setChatMemory(chatMemoryConfig);
        LockProperties lockProperties = new LockProperties();
        lockProperties.setCompressionLeaseTimeoutMs(60_000);

        DialogueBatchDocument batch = new DialogueBatchDocument(
                "batch-1",
                "default",
                "processing",
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of()
        );
        when(dialogueMarkdownService.promoteCollectingToProcessingIfReady(anyString(), anyInt(), anyInt()))
                .thenReturn(Optional.of(batch));

        AtomicInteger callCounter = new AtomicInteger();
        doAnswer(invocation -> {
            Runnable onSuccess = invocation.getArgument(3);
            Runnable onFailure = invocation.getArgument(4);
            if (callCounter.incrementAndGet() == 1) {
                onFailure.run();
            } else {
                onSuccess.run();
            }
            return null;
        }).when(compressor).compressAsync(anyString(), anyList(), anyList(), any(), any());

        ArchivableChatMemory chatMemory = new ArchivableChatMemory(
                "default",
                compressor,
                chatMessageAppender,
                dialogueMarkdownService,
                assistantProperties,
                lockProperties,
                chatLogRepository
        );

        chatMemory.add(UserMessage.from("第一轮用户消息"));
        chatMemory.add(AiMessage.from("第一轮 AI 回复"));
        chatMemory.add(UserMessage.from("第二轮用户消息"));
        chatMemory.add(AiMessage.from("第二轮 AI 回复"));

        verify(compressor, times(2)).compressAsync(anyString(), anyList(), anyList(), any(), any());
        verify(dialogueMarkdownService, times(1)).acknowledgeProcessing("default");
    }
}
