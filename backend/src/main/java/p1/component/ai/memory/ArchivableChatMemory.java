package p1.component.ai.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.extern.slf4j.Slf4j;
import p1.config.prop.AssistantProperties;
import p1.config.prop.LockProperties;
import p1.model.ChatLogEntity;
import p1.repo.db.ChatLogRepository;
import p1.service.markdown.DialogueMarkdownService;
import p1.utils.ChatMessageUtil;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ArchivableChatMemory implements ChatMemory {

    private final String sessionId;
    private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();
    private final AtomicReference<CompressionLease> activeCompression = new AtomicReference<>();

    private final MemoryCompressor compressor;
    private final ChatMessageAppender chatMessageAppender;
    private final DialogueMarkdownService dialogueMarkdownService;
    private final ChatLogRepository chatLogRepository;

    private final int triggerThreshold;
    private final int compressCount;
    private final Duration compressionLeaseTimeout;
    private final Set<String> referenceBuffer = ConcurrentHashMap.newKeySet();

    public ArchivableChatMemory(String sessionId,
                                MemoryCompressor compressor,
                                ChatMessageAppender chatMessageAppender,
                                DialogueMarkdownService dialogueMarkdownService,
                                AssistantProperties assistantProperties,
                                LockProperties lockProperties,
                                ChatLogRepository chatLogRepository) {
        this.sessionId = sessionId;
        this.compressor = compressor;
        this.chatMessageAppender = chatMessageAppender;
        this.dialogueMarkdownService = dialogueMarkdownService;
        this.triggerThreshold = assistantProperties.getChatMemory().getTriggerCompressThreshold();
        this.compressCount = assistantProperties.getChatMemory().getCompressCount();
        this.compressionLeaseTimeout = Duration.ofMillis(lockProperties.getCompressionLeaseTimeoutMs());
        this.chatLogRepository = chatLogRepository;
    }

    @Override
    public Object id() {
        return sessionId;
    }

    @Override
    public void add(ChatMessage message) {
        log.debug("消息队列新增: {}", message);
        LocalDateTime time = LocalDateTime.now();
        ChatMessage withTimeChatMessage = ChatMessageUtil.withTimestamp(message, time);

        // 保存消息到日志库
        ChatLogEntity entity = new ChatLogEntity(message, sessionId);
        entity.setTime(time);
        chatLogRepository.save(entity);

        // 保持系统提示词置顶
        if (withTimeChatMessage instanceof SystemMessage) {
            messages.removeIf(existing -> existing instanceof SystemMessage);
            messages.addFirst(withTimeChatMessage);
            return;
        }

        boolean isFinalTurnMessage = ChatMessageUtil.isAiFinalResponseMessage(withTimeChatMessage);
        if (isFinalTurnMessage) {
            log.trace("对话轮次结束，开始清理上下文。");
            purifyContext();
        }

        chatMessageAppender.append(sessionId, withTimeChatMessage);
        messages.add(withTimeChatMessage);

        compressMemory(isFinalTurnMessage);
    }

    private void compressMemory(boolean isFinalTurnMessage) {
        if (!isFinalTurnMessage || messages.size() < triggerThreshold) {
            return;
        }

        CompressionLease lease = tryAcquireCompressionLease();
        if (lease == null) {
            return;
        }

        log.info("[记忆压缩触发] 当前消息数达到阈值 {}，准备压缩，sessionId={}，leaseId={}",
                triggerThreshold, sessionId, lease.leaseId());

        List<ChatMessage> toCompress = new ArrayList<>(messages.subList(0, compressCount));
        List<String> references = new ArrayList<>(referenceBuffer);

        ChatMessageAppender.DialogueBatch processingBatch;
        try {
            processingBatch = dialogueMarkdownService
                    .promoteCollectingToProcessingIfReady(sessionId, triggerThreshold, compressCount)
                    .map(batch -> new ChatMessageAppender.DialogueBatch(batch.id(), batch.sessionId(), batch.messages()))
                    .orElse(null);
        } catch (Exception e) {
            log.error("[记忆压缩失败] 准备 processing 批次时发生异常，sessionId={}，leaseId={}",
                    sessionId, lease.leaseId(), e);
            releaseCompressionLease(lease, "准备 processing 失败");
            return;
        }

        if (processingBatch == null) {
            log.warn("[记忆压缩] 没有可处理的 processing 批次，释放租约，sessionId={}，leaseId={}",
                    sessionId, lease.leaseId());
            releaseCompressionLease(lease, "没有 processing 批次");
            return;
        }

        compressor.compressAsync(sessionId, toCompress, references, () -> onCompressionSuccess(lease, toCompress), () -> {
            if (isLeaseCurrent(lease)) {
                log.warn("[记忆压缩失败] 后台压缩失败，释放租约，sessionId={}，batchId={}，leaseId={}",
                        sessionId, processingBatch.batchId(), lease.leaseId());
                releaseCompressionLease(lease, "后台压缩失败");
            } else {
                log.warn("[记忆压缩] 收到过期失败回调，已忽略，sessionId={}，batchId={}，leaseId={}",
                        sessionId, processingBatch.batchId(), lease.leaseId());
            }
        });
    }

    private void onCompressionSuccess(CompressionLease lease, List<ChatMessage> toCompress) {
        if (!isLeaseCurrent(lease)) {
            log.warn("[记忆压缩] 收到过期成功回调，忽略后续收尾，sessionId={}，leaseId={}", sessionId, lease.leaseId());
            return;
        }

        try {
            dialogueMarkdownService.acknowledgeProcessing(sessionId);

            for (int i = 0; i < toCompress.size(); i++) {
                if (!messages.isEmpty()) {
                    messages.removeFirst();
                }
            }
            referenceBuffer.clear();
            log.info("[记忆压缩完成] 当前内存窗口剩余 {} 条消息，压缩 {} 条消息，sessionId={}，leaseId={}",
                    messages.size(), toCompress.size(), sessionId, lease.leaseId());
        } catch (Exception e) {
            log.error("[记忆压缩失败] 压缩成功后的确认阶段异常，sessionId={}，leaseId={}",
                    sessionId, lease.leaseId(), e);
        } finally {
            releaseCompressionLease(lease, "压缩流程结束");
        }
    }

    private CompressionLease tryAcquireCompressionLease() {
        LocalDateTime now = LocalDateTime.now();
        CompressionLease current = activeCompression.get();
        if (current == null) {
            CompressionLease newLease = new CompressionLease(UUID.randomUUID().toString(), now);
            return activeCompression.compareAndSet(null, newLease) ? newLease : null;
        }

        if (!isLeaseExpired(current, now)) {
            log.debug("[记忆压缩] 当前已有压缩任务执行中，跳过本次触发，sessionId={}，leaseId={}，startedAt={}",
                    sessionId, current.leaseId(), current.startedAt());
            return null;
        }

        CompressionLease replacement = new CompressionLease(UUID.randomUUID().toString(), now);
        if (activeCompression.compareAndSet(current, replacement)) {
            log.warn("[记忆压缩] sessionId={} 当前锁因为超时而被移除。oldLeaseId={}，newLeaseId={}，startedAt={}，timeoutMs={}",
                    sessionId,
                    current.leaseId(),
                    replacement.leaseId(),
                    current.startedAt(),
                    compressionLeaseTimeout.toMillis());
            return replacement;
        }

        return null;
    }

    private boolean isLeaseCurrent(CompressionLease lease) {
        return lease != null && lease.equals(activeCompression.get());
    }

    private boolean isLeaseExpired(CompressionLease lease, LocalDateTime referenceTime) {
        return lease != null && lease.startedAt().plus(compressionLeaseTimeout).isBefore(referenceTime);
    }

    private void releaseCompressionLease(CompressionLease lease, String reason) {
        if (lease == null) {
            return;
        }

        if (activeCompression.compareAndSet(lease, null)) {
            log.info("[记忆压缩] sessionId={} 已释放锁，leaseId={}，reason={}",
                    sessionId, lease.leaseId(), reason);
        }
    }

    private void purifyContext() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);

            if (message instanceof ToolExecutionResultMessage toolMessage) {
                if (toolMessage.text() != null && toolMessage.text().contains("[记忆ID:")) {
                    referenceBuffer.add(toolMessage.text());
                    log.debug("[上下文清理] 捕获带记忆 ID 的工具结果：{}", toolMessage.text());
                }
                messages.remove(i);
            } else if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                messages.remove(i);
            } else if (message instanceof UserMessage) {
                break;
            }
        }
        log.info("[上下文清理] 清理完成，当前窗口剩余 {} 条消息。", messages.size());
    }

    @Override
    public List<ChatMessage> messages() {
        return messages;
    }

    @Override
    public void clear() {
        messages.clear();
        activeCompression.set(null);
    }

    private record CompressionLease(String leaseId, LocalDateTime startedAt) {
    }
}
