package p1.service.markdown;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import p1.model.enums.DialogueMessageRole;
import p1.repo.markdown.DialogueBatchMarkdownRepository;
import p1.repo.markdown.RawDialogueMarkdownRepository;
import p1.repo.markdown.model.DialogueBatchDocument;
import p1.repo.markdown.model.DialogueBatchMessage;
import p1.repo.markdown.model.MarkdownDocument;
import p1.repo.markdown.model.RawDialogueMessageRef;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class DialogueMarkdownService {

    private static final DateTimeFormatter BATCH_ID_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Duration COLLECTING_REOPEN_WINDOW = Duration.ofMinutes(30);

    private final RawDialogueMarkdownRepository rawDialogueMarkdownRepository;
    private final DialogueBatchMarkdownRepository dialogueBatchMarkdownRepository;
    private final RawDialogueMarkdownAssembler rawDialogueMarkdownAssembler;
    private final DialogueBatchMarkdownMapper dialogueBatchMarkdownMapper;
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    /**
     * 追加一条新的对话消息。
     * 这里会同时做两件事：
     * 1. 追加到 raw daily note，作为原始对话记录。
     * 2. 追加到 collecting.md，作为后续压缩的待处理 backlog。
     */
    public RawDialogueMessageRef appendDialogueMessage(String sessionId,
                                                       DialogueMessageRole role,
                                                       String text,
                                                       LocalDateTime createdAt) {
        String cleanText = normalizeText(text);
        if (cleanText.isBlank()) {
            throw new IllegalArgumentException("raw dialogue text must not be blank");
        }

        LocalDateTime timestamp = createdAt == null ? LocalDateTime.now() : createdAt;
        var date = timestamp.toLocalDate();

        // raw note 追加和 collecting batch 追加必须放在同一个 session 锁里，
        // 否则并发写入时，可能出现消息只落到其中一边的中间状态。
        synchronized (lockForSession(sessionId)) {
            MarkdownDocument rawNote = rawDialogueMarkdownRepository.findDailyNote(sessionId, date)
                    .orElseGet(() -> rawDialogueMarkdownAssembler.createDailyNote(date));

            String messageId = buildMessageId();
            MarkdownDocument updatedRawNote = rawDialogueMarkdownAssembler
                    .appendMessage(rawNote, sessionId, role, cleanText, timestamp, messageId);
            rawDialogueMarkdownRepository.saveDailyNote(sessionId, date, updatedRawNote);

            RawDialogueMessageRef rawRef = new RawDialogueMessageRef(
                    rawDialogueMarkdownRepository.relativeDailyNotePath(sessionId, date),
                    messageId,
                    role,
                    timestamp
            );

            // collecting.md 是当前 session 的持久 backlog。
            // 1 小时内重新接入，继续复用同一个 collecting；
            // 超过 1 小时则把旧 collecting 封口到 processing，再为新消息开启新的 collecting。
            Optional<DialogueBatchDocument> collectingCandidate = loadCollectingForAppend(sessionId, timestamp);
            DialogueBatchDocument collecting = collectingCandidate
                    .orElseGet(() -> {
                        DialogueBatchDocument newCollecting = new DialogueBatchDocument(
                                buildBatchId(),
                                sessionId,
                                "collecting",
                                timestamp,
                                timestamp,
                                null,
                                new ArrayList<>()
                        );
                        log.info("[对话批次] 新建 collecting，sessionId={}, batchId={}, createdAt={}",
                                sessionId, newCollecting.id(), timestamp);
                        return newCollecting;
                    });

            // 向collecting的messages列表追加信息
            List<DialogueBatchMessage> messages = new ArrayList<>(collecting.messages());
            messages.add(new DialogueBatchMessage(
                    rawRef.messageId(),
                    role,
                    cleanText,
                    timestamp,
                    rawRef.notePath(),
                    rawRef.messageId()
            ));

            DialogueBatchDocument updatedCollecting = new DialogueBatchDocument(
                    collecting.id(),
                    sessionId,
                    "collecting",
                    collecting.createdAt(),
                    timestamp,
                    null,
                    messages
            );
            dialogueBatchMarkdownRepository.saveCollecting(sessionId, dialogueBatchMarkdownMapper.toMarkdown(updatedCollecting));
            log.debug("[对话批次] collecting 已追加消息，sessionId={}, batchId={}, 当前消息数={}",
                    sessionId, updatedCollecting.id(), updatedCollecting.messageCount());
            return rawRef;
        }
    }

    public Optional<DialogueBatchDocument> findProcessing(String sessionId) {
        return dialogueBatchMarkdownRepository.findProcessing(sessionId).map(dialogueBatchMarkdownMapper::fromMarkdown);
    }

    /**
     * 如果 collecting.md 已经积累到阈值，就从头部切出一段生成 processing.md。
     * 注意：
     * - collecting.md 代表“整个待处理队列”
     * - processing.md 代表“当前这一轮正在压缩的快照”
     * 这里不会立刻改写 collecting.md，只有压缩成功后才会正式扣除。
     */
    public Optional<DialogueBatchDocument> promoteCollectingToProcessingIfReady(String sessionId,
                                                                                int triggerThreshold,
                                                                                int compressCount) {
        synchronized (lockForSession(sessionId)) {
            // 运行时压缩和启动恢复都会走到这里。
            // 如果 processing.md 已经存在，就直接复用，避免出现第二个进行中的 batch。
            Optional<DialogueBatchDocument> existingProcessing = findProcessing(sessionId);
            if (existingProcessing.isPresent()) {
                DialogueBatchDocument processing = existingProcessing.get();
                log.info("[对话批次] 已存在 processing，直接复用，sessionId={}, batchId={}, 消息数={}",
                        sessionId, processing.id(), processing.messageCount());
                return existingProcessing;
            }

            DialogueBatchDocument collecting = loadCollectingForProcessing(sessionId, LocalDateTime.now()).orElse(null);
            if (collecting == null) {
                return findProcessing(sessionId);
            }
            if (collecting.messageCount() < triggerThreshold) {
                log.debug("[对话批次] collecting 未达到压缩阈值，sessionId={}, batchId={}, 当前消息数={}, 阈值={}",
                        sessionId, collecting.id(), collecting.messageCount(), triggerThreshold);
                return Optional.empty();
            }

            // processing.md 只快照当前要压缩的头部片段。
            // 这里先不修改 collecting.md，等压缩成功后再正式扣除。
            int processingCount = Math.min(compressCount, collecting.messageCount());
            List<DialogueBatchMessage> processingMessages = new ArrayList<>(collecting.messages().subList(0, processingCount));
            DialogueBatchDocument processing = new DialogueBatchDocument(
                    collecting.id(),
                    sessionId,
                    "processing",
                    collecting.createdAt(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    processingMessages
            );
            dialogueBatchMarkdownRepository.saveProcessing(sessionId, dialogueBatchMarkdownMapper.toMarkdown(processing));
            log.info("[对话批次] collecting 提升为 processing，sessionId={}, batchId={}, 本次压缩消息数={}, collecting 总消息数={}",
                    sessionId, processing.id(), processing.messageCount(), collecting.messageCount());
            return Optional.of(processing);
        }
    }

    /**
     * 确认 processing.md 对应的这一轮压缩已经成功完成。
     * 处理方式不是直接删除 collecting.md，而是把 processing.md 中已经处理过的消息
     * 从 collecting.md 里扣除；扣除完成后，再删除 processing.md。
     * 这样可以保证：
     * - 压缩过程中如果崩溃，collecting.md 仍然保留完整 backlog
     * - 只有真正成功后，待处理队列才会被正式裁剪
     */
    public void acknowledgeProcessing(String sessionId) {
        synchronized (lockForSession(sessionId)) {
            DialogueBatchDocument processing = findProcessing(sessionId).orElse(null);
            if (processing == null) {
                log.debug("[对话批次] acknowledge 跳过，processing 不存在，sessionId={}", sessionId);
                return;
            }

            DialogueBatchDocument collecting = loadCollecting(sessionId).orElse(null);
            if (collecting != null) {
                // 确认成功的方式，是把已处理的 message id 从 collecting.md 中扣掉。
                // 这样处理期间 collecting 一直保持完整，崩溃恢复时更安全。
                Set<String> processedIds = new LinkedHashSet<>();
                processing.messages().forEach(message -> processedIds.add(message.messageId()));

                List<DialogueBatchMessage> remainingMessages = collecting.messages().stream()
                        .filter(message -> !processedIds.contains(message.messageId()))
                        .toList();

                if (remainingMessages.isEmpty()) {
                    dialogueBatchMarkdownRepository.deleteCollecting(sessionId);
                    log.info("[对话批次] processing 确认完成，collecting 已清空，sessionId={}, batchId={}",
                            sessionId, processing.id());
                } else {
                    DialogueBatchDocument updatedCollecting = new DialogueBatchDocument(
                            collecting.id(),
                            sessionId,
                            "collecting",
                            collecting.createdAt(),
                            LocalDateTime.now(),
                            null,
                            remainingMessages
                    );
                    dialogueBatchMarkdownRepository.saveCollecting(sessionId, dialogueBatchMarkdownMapper.toMarkdown(updatedCollecting));
                    log.info("[对话批次] processing 确认完成，collecting 已扣除已处理消息，sessionId={}, batchId={}, collecting 剩余消息数={}",
                            sessionId, processing.id(), updatedCollecting.messageCount());
                }
            } else {
                log.info("[对话批次] processing 确认完成，本轮为独立 processing，sessionId={}, batchId={}",
                        sessionId, processing.id());
            }

            dialogueBatchMarkdownRepository.deleteProcessing(sessionId);
            log.info("[对话批次] processing 已删除，sessionId={}, batchId={}", sessionId, processing.id());
        }
    }

    /**
     * 返回当前存在 collecting.md 或 processing.md 的 session。
     * 启动恢复时会用它来定位哪些 session 还有未完成任务。
     */
    public Set<String> listSessionIdsWithOpenBatches() {
        return dialogueBatchMarkdownRepository.listSessionIdsWithOpenBatches();
    }

    private Optional<DialogueBatchDocument> loadCollecting(String sessionId) {
        return dialogueBatchMarkdownRepository.findCollecting(sessionId).map(dialogueBatchMarkdownMapper::fromMarkdown);
    }

    /**
     * 读取“当前仍然可继续追加”的 collecting。
     * 规则：
     * - 如果最后活跃时间在 1 小时内，继续视为同一个 collecting
     * - 如果已经超过 1 小时，且当前没有 processing，则把旧 collecting 整体封口到 processing
     *   然后返回空，让调用方为新消息创建一个新的 collecting
     * - 如果已经有 processing 在执行中，为了避免覆盖 backlog，暂时保留原 collecting，
     *   也就是“超时直接转 processing”只会发生在当前没有 processing.md 的前提下
     */
    private Optional<DialogueBatchDocument> loadCollectingForAppend(String sessionId, LocalDateTime referenceTime) {
        DialogueBatchDocument collecting = loadCollecting(sessionId).orElse(null);
        if (collecting == null) {
            return Optional.empty();
        }
        if (!isCollectingExpired(collecting, referenceTime)) {
            log.debug("[对话批次] 复用 collecting，sessionId={}, batchId={}, 最近活跃时间={}",
                    sessionId, collecting.id(), collecting.updatedAt() != null ? collecting.updatedAt() : collecting.createdAt());
            return Optional.of(collecting);
        }
        if (findProcessing(sessionId).isPresent()) {
            log.info("[对话批次] collecting 已超时，但已有 processing 在执行，暂不封口 collecting，sessionId={}, batchId={}",
                    sessionId, collecting.id());
            return Optional.of(collecting);
        }

        sealCollectingToProcessing(sessionId, collecting, referenceTime, "超时重连，旧 collecting 封口");
        return Optional.empty();
    }

    /**
     * 读取“当前可进入压缩判断”的 collecting。
     * 如果 collecting 已经过了 1 小时空窗，就不再走阈值判断，而是直接整批封口到 processing。
     * 同样地，这个动作也要求当前没有 processing.md；如果已经有 processing 在执行，
     * 就优先让那一批先完成，避免一个 session 同时出现两个 processing。
     */
    private Optional<DialogueBatchDocument> loadCollectingForProcessing(String sessionId, LocalDateTime referenceTime) {
        DialogueBatchDocument collecting = loadCollecting(sessionId).orElse(null);
        if (collecting == null) {
            return Optional.empty();
        }
        if (!isCollectingExpired(collecting, referenceTime)) {
            return Optional.of(collecting);
        }
        if (findProcessing(sessionId).isPresent()) {
            log.info("[对话批次] collecting 已超时，但已有 processing 在执行，等待当前 processing 完成后再处理 collecting，sessionId={}, batchId={}",
                    sessionId, collecting.id());
            return Optional.empty();
        }

        sealCollectingToProcessing(sessionId, collecting, referenceTime, "collecting 超时，直接转 processing");
        return Optional.empty();
    }

    /**
     * 把一个超时的 collecting 整体封口成 processing。
     * 这里会直接删除原 collecting，因为它的整批内容已经完整移交给 processing。
     * 后续如果压缩成功，acknowledge 时只需要删除 processing 即可。
     */
    private void sealCollectingToProcessing(String sessionId,
                                            DialogueBatchDocument collecting,
                                            LocalDateTime referenceTime,
                                            String reason) {
        DialogueBatchDocument processing = new DialogueBatchDocument(
                collecting.id(),
                sessionId,
                "processing",
                collecting.createdAt(),
                referenceTime,
                referenceTime,
                new ArrayList<>(collecting.messages())
        );
        dialogueBatchMarkdownRepository.saveProcessing(sessionId, dialogueBatchMarkdownMapper.toMarkdown(processing));
        dialogueBatchMarkdownRepository.deleteCollecting(sessionId);
        log.info("[对话批次] collecting 已整体封口为 processing，sessionId={}, batchId={}, 消息数={}, 原因={}",
                sessionId, processing.id(), processing.messageCount(), reason);
    }

    private boolean isCollectingExpired(DialogueBatchDocument collecting, LocalDateTime referenceTime) {
        LocalDateTime lastActiveAt = collecting.updatedAt() != null ? collecting.updatedAt() : collecting.createdAt();
        if (lastActiveAt == null) {
            return false;
        }
        return lastActiveAt.plus(COLLECTING_REOPEN_WINDOW).isBefore(referenceTime);
    }

    private Object lockForSession(String sessionId) {
        // 进程内按 session 串行化文件写入。
        return sessionLocks.computeIfAbsent(sessionId, ignored -> new Object());
    }

    private String buildMessageId() {
        return "dm-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String buildBatchId() {
        return "batch-" + BATCH_ID_TIME_FORMATTER.format(LocalDateTime.now()) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.strip()
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
