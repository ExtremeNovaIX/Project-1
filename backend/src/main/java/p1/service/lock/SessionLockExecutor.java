package p1.service.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p1.config.prop.LockProperties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionLockExecutor {

    private final LockProperties lockProperties;
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public <T> T execute(String sessionId, String operation, Supplier<T> action) {
        ReentrantLock lock = sessionLocks.computeIfAbsent(sessionId, ignored -> new ReentrantLock());
        int retryCount = Math.max(1, lockProperties.getSessionLockRetryCount());
        long waitTimeoutMs = Math.max(1, lockProperties.getSessionLockWaitTimeoutMs());
        long retryDelayMs = Math.max(0, lockProperties.getSessionLockRetryDelayMs());

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            boolean acquired;
            try {
                acquired = lock.tryLock(waitTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("session lock interrupted, sessionId=" + sessionId + ", operation=" + operation, e);
            }

            if (acquired) {
                try {
                    return action.get();
                } finally {
                    lock.unlock();
                }
            }

            log.warn("[对话批次锁] 获取锁超时，sessionId={}，operation={}，attempt={}/{}，waitTimeoutMs={}",
                    sessionId, operation, attempt, retryCount, waitTimeoutMs);

            if (attempt < retryCount && retryDelayMs > 0) {
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("session lock retry interrupted, sessionId=" + sessionId + ", operation=" + operation, e);
                }
            }
        }

        throw new IllegalStateException("failed to acquire session lock, sessionId=" + sessionId + ", operation=" + operation);
    }

    public void execute(String sessionId, String operation, Runnable action) {
        execute(sessionId, operation, () -> {
            action.run();
            return null;
        });
    }
}
