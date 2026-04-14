package p1.service.markdown;

import org.junit.jupiter.api.Test;
import p1.config.prop.LockProperties;
import p1.model.enums.DialogueMessageRole;
import p1.repo.markdown.DialogueBatchMarkdownRepository;
import p1.repo.markdown.RawDialogueMarkdownRepository;
import p1.repo.markdown.model.MarkdownDocument;
import p1.service.lock.SessionLockExecutor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogueMarkdownServiceLockTest {

    @Test
    void shouldFailFastOnLockTimeoutAndRecoverAfterLockReleased() throws Exception {
        LockProperties lockProperties = new LockProperties();
        lockProperties.setSessionLockWaitTimeoutMs(50);
        lockProperties.setSessionLockRetryCount(2);
        lockProperties.setSessionLockRetryDelayMs(10);

        RawDialogueMarkdownRepository rawRepository = mock(RawDialogueMarkdownRepository.class);
        DialogueBatchMarkdownRepository batchRepository = mock(DialogueBatchMarkdownRepository.class);
        RawDialogueMarkdownAssembler rawAssembler = new RawDialogueMarkdownAssembler();
        DialogueBatchMarkdownMapper batchMapper = new DialogueBatchMarkdownMapper();

        MarkdownDocument initialDailyNote = rawAssembler.createDailyNote(LocalDate.now());
        CountDownLatch firstCallEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);
        AtomicInteger appendCalls = new AtomicInteger();

        when(rawRepository.findDailyNote(anyString(), any())).thenReturn(Optional.of(initialDailyNote));
        when(rawRepository.relativeDailyNotePath(anyString(), any())).thenReturn("sessions/default/raw/dialogues/2026/2026-04/2026-04-15");
        when(batchRepository.findCollecting(anyString())).thenReturn(Optional.empty());
        when(batchRepository.findProcessing(anyString())).thenReturn(Optional.empty());
        doNothing().when(rawRepository).saveDailyNote(anyString(), any(), any());
        doNothing().when(batchRepository).saveCollecting(anyString(), any());

        DialogueMarkdownService service = new DialogueMarkdownService(
                rawRepository,
                batchRepository,
                new RawDialogueMarkdownAssembler() {
                    @Override
                    public MarkdownDocument appendMessage(MarkdownDocument note,
                                                          String sessionId,
                                                          DialogueMessageRole role,
                                                          String cleanText,
                                                          LocalDateTime timestamp,
                                                          String messageId) {
                        int call = appendCalls.incrementAndGet();
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
                        return super.appendMessage(note, sessionId, role, cleanText, timestamp, messageId);
                    }
                },
                batchMapper,
                new SessionLockExecutor(lockProperties)
        );

        AtomicReference<Throwable> firstThreadFailure = new AtomicReference<>();
        Thread firstThread = new Thread(() -> {
            try {
                service.appendDialogueMessage("default", DialogueMessageRole.USER, "第一条消息", LocalDateTime.now());
            } catch (Throwable t) {
                firstThreadFailure.set(t);
            }
        });
        firstThread.start();

        assertTrue(firstCallEntered.await(1, TimeUnit.SECONDS), "第一条请求应该已经进入临界区");

        IllegalStateException timeout = assertThrows(IllegalStateException.class,
                () -> service.appendDialogueMessage("default", DialogueMessageRole.USER, "第二条消息", LocalDateTime.now()));
        assertTrue(timeout.getMessage().contains("failed to acquire session lock"));

        releaseFirstCall.countDown();
        firstThread.join(2000);
        assertTrue(!firstThread.isAlive(), "第一条请求应该在释放锁后结束");
        assertTrue(firstThreadFailure.get() == null, "第一条请求不应该失败");

        Object thirdResult = service.appendDialogueMessage("default", DialogueMessageRole.USER, "第三条消息", LocalDateTime.now());
        assertNotNull(thirdResult, "释放锁后应允许后续请求继续进入");
    }
}
