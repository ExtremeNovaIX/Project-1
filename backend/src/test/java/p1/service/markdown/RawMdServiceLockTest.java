package p1.service.markdown;

import org.junit.jupiter.api.Test;
import p1.config.prop.LockProperties;
import p1.model.enums.DialogueMessageRole;
import p1.repo.markdown.RawBatchMdRepository;
import p1.repo.markdown.RawMdRepository;
import p1.repo.markdown.model.MarkdownDocument;
import p1.repo.markdown.model.RawBatchDocument;
import p1.service.lock.SessionLockExecutor;
import p1.service.markdown.assembler.RawBatchMdAssembler;
import p1.service.markdown.assembler.RawMdAssembler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RawMdServiceLockTest {

    @Test
    void shouldKeepLifecycleTimeNearServerNowWhenMessageCarriesFutureTimestamp() {
        LockProperties lockProperties = new LockProperties();

        RawMdRepository rawRepository = mock(RawMdRepository.class);
        RawBatchMdRepository batchRepository = mock(RawBatchMdRepository.class);
        RawBatchMdAssembler batchMapper = new RawBatchMdAssembler();

        when(rawRepository.relativeDailyNotePath(anyString(), any())).thenReturn("sessions/default/raw/dialogues/2026/2026-04/2026-04-20");
        doNothing().when(rawRepository).createDailyNoteIfMissing(anyString(), any(), any());
        doNothing().when(rawRepository).appendToDailyNote(anyString(), any(), anyString());
        doNothing().when(batchRepository).saveCollecting(anyString(), any());

        LocalDateTime existingCreatedAt = LocalDateTime.now().minusMinutes(10);
        LocalDateTime existingUpdatedAt = LocalDateTime.now().minusMinutes(5);
        RawBatchDocument existingCollecting = new RawBatchDocument(
                "batch-existing",
                "default",
                "collecting",
                existingCreatedAt,
                existingUpdatedAt,
                null,
                List.of()
        );
        when(batchRepository.findCollecting("default"))
                .thenReturn(Optional.of(batchMapper.toMarkdown(existingCollecting)));
        when(batchRepository.findProcessing("default")).thenReturn(Optional.empty());

        RawMdService service = new RawMdService(
                rawRepository,
                batchRepository,
                new RawMdAssembler(),
                batchMapper,
                new SessionLockExecutor(lockProperties)
        );

        LocalDateTime futureMessageTime = LocalDateTime.now().plusDays(1);
        LocalDateTime startedAt = LocalDateTime.now();
        service.appendRawMessage("default", DialogueMessageRole.USER, "future timestamp payload");
        LocalDateTime finishedAt = LocalDateTime.now();

        var collectingCaptor = forClass(MarkdownDocument.class);
        verify(batchRepository).saveCollecting(eq("default"), collectingCaptor.capture());
        verify(batchRepository, never()).saveProcessing(anyString(), any());

        RawBatchDocument savedCollecting = batchMapper.fromMarkdown(collectingCaptor.getValue());
        assertEquals("batch-existing", savedCollecting.id());
        assertEquals(existingCreatedAt, savedCollecting.createdAt());
        assertNotNull(savedCollecting.updatedAt());
        assertFalse(savedCollecting.updatedAt().isBefore(startedAt.minusSeconds(1)));
        assertFalse(savedCollecting.updatedAt().isAfter(finishedAt.plusSeconds(1)));
        assertEquals(1, savedCollecting.messageCount());
        assertEquals(futureMessageTime, savedCollecting.messages().getFirst().createdAt());
    }

    @Test
    void shouldFailFastOnLockTimeoutAndRecoverAfterLockReleased() throws Exception {
        LockProperties lockProperties = new LockProperties();
        lockProperties.setSessionLockWaitTimeoutMs(50);
        lockProperties.setSessionLockRetryCount(2);
        lockProperties.setSessionLockRetryDelayMs(10);

        RawMdRepository rawRepository = mock(RawMdRepository.class);
        RawBatchMdRepository batchRepository = mock(RawBatchMdRepository.class);
        RawBatchMdAssembler batchMapper = new RawBatchMdAssembler();

        CountDownLatch firstCallEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);
        AtomicInteger buildBlockCalls = new AtomicInteger();

        when(rawRepository.relativeDailyNotePath(anyString(), any())).thenReturn("sessions/default/raw/dialogues/2026/2026-04/2026-04-15");
        when(batchRepository.findCollecting(anyString())).thenReturn(Optional.empty());
        when(batchRepository.findProcessing(anyString())).thenReturn(Optional.empty());
        doNothing().when(rawRepository).createDailyNoteIfMissing(anyString(), any(), any());
        doNothing().when(rawRepository).appendToDailyNote(anyString(), any(), anyString());
        doNothing().when(batchRepository).saveCollecting(anyString(), any());

        RawMdService service = new RawMdService(
                rawRepository,
                batchRepository,
                new RawMdAssembler() {
                    @Override
                    public String buildMessageBlock(DialogueMessageRole role,
                                                    String cleanText,
                                                    LocalDateTime timestamp,
                                                    String messageId) {
                        int call = buildBlockCalls.incrementAndGet();
                        if (call == 1) {
                            firstCallEntered.countDown();
                            try {
                                if (!releaseFirstCall.await(2, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("test latch wait timed out");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(e);
                            }
                        }
                        return super.buildMessageBlock(role, cleanText, timestamp, messageId);
                    }
                },
                batchMapper,
                new SessionLockExecutor(lockProperties)
        );

        AtomicReference<Throwable> firstThreadFailure = new AtomicReference<>();
        Thread firstThread = new Thread(() -> {
            try {
                service.appendRawMessage("default", DialogueMessageRole.USER, "第一条消息");
            } catch (Throwable t) {
                firstThreadFailure.set(t);
            }
        });
        firstThread.start();

        assertTrue(firstCallEntered.await(1, TimeUnit.SECONDS), "第一条请求应该已经进入临界区");

        IllegalStateException timeout = assertThrows(IllegalStateException.class,
                () -> service.appendRawMessage("default", DialogueMessageRole.USER, "第二条消息"));
        assertTrue(timeout.getMessage().contains("failed to acquire session lock"));

        releaseFirstCall.countDown();
        firstThread.join(2000);
        assertTrue(!firstThread.isAlive(), "第一条请求应该在释放锁后结束");
        assertTrue(firstThreadFailure.get() == null, "第一条请求不应该失败");

        Object thirdResult = service.appendRawMessage("default", DialogueMessageRole.USER, "第三条消息");
        assertNotNull(thirdResult, "释放锁后应允许后续请求继续进入");
    }
}
