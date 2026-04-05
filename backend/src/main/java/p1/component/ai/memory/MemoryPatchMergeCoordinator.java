package p1.component.ai.memory;

import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import p1.component.ai.service.FactExtractionAiService;
import p1.component.ai.service.MemoryPatchMergeAiService;
import p1.config.prop.AssistantProperties;
import p1.model.ExtractedMemoryEventDTO;
import p1.model.MemoryArchiveEntity;
import p1.model.MemoryPatchEntity;
import p1.repo.MemoryArchiveRepository;
import p1.repo.MemoryPatchRepository;
import p1.service.EmbeddingService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MemoryPatchMergeCoordinator {

    private static final DateTimeFormatter PATCH_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ARCHIVE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MemoryArchiveRepository archiveRepo;
    private final MemoryPatchRepository patchRepo;
    private final FactExtractionAiService factExtractionAiService;
    private final MemoryPatchMergeAiService patchMergeAiService;
    private final EmbeddingService embeddingService;
    private final AssistantProperties props;
    private final Executor taskExecutor;

    private final Set<Long> mergingTargets = ConcurrentHashMap.newKeySet();

    public MemoryPatchMergeCoordinator(MemoryArchiveRepository archiveRepo,
                                       MemoryPatchRepository patchRepo,
                                       FactExtractionAiService factExtractionAiService,
                                       MemoryPatchMergeAiService patchMergeAiService,
                                       EmbeddingService embeddingService,
                                       AssistantProperties props,
                                       @Qualifier("asyncTaskExecutor") Executor taskExecutor) {
        this.archiveRepo = archiveRepo;
        this.patchRepo = patchRepo;
        this.factExtractionAiService = factExtractionAiService;
        this.patchMergeAiService = patchMergeAiService;
        this.embeddingService = embeddingService;
        this.props = props;
        this.taskExecutor = taskExecutor;
    }

    public void onPatchSaved(Long targetMemoryId) {
        if (targetMemoryId == null) {
            return;
        }

        long patchCount = patchRepo.countByTargetMemoryIdAndCompressedFalse(targetMemoryId);
        int threshold = props.getChatMemory().getPatchMergeThreshold();

        log.info("[Patch 合并检测] 目标记忆 ID {} 当前未压缩 Patch 数量为 {}。", targetMemoryId, patchCount);
        if (patchCount >= threshold) {
            log.info("[Patch 合并触发] 目标记忆 ID {} 的未压缩 Patch 数量达到阈值 {}。", targetMemoryId, threshold);
            submitMergeTask(targetMemoryId, "PATCH_THRESHOLD");
        }
    }

    @Scheduled(
            initialDelayString = "${assistant.chat-memory.patch-merge-scan-fixed-delay-ms:300000}",
            fixedDelayString = "${assistant.chat-memory.patch-merge-scan-fixed-delay-ms:1800000}"
    )
    public void scanPendingPatches() {
        List<Long> targetIds = patchRepo.findDistinctTargetMemoryIdsByCompressedFalse();
        if (targetIds.isEmpty()) {
            return;
        }

        log.info("[Patch 定时扫描] 扫描到 {} 个存在未压缩 Patch 的目标记忆。", targetIds.size());
        for (Long targetId : targetIds) {
            submitMergeTask(targetId, "SCHEDULED_SCAN");
        }
    }

    public void migrateAllPendingPatches() {
        List<Long> targetIds = patchRepo.findDistinctTargetMemoryIdsByCompressedFalse();
        if (targetIds.isEmpty()) {
            return;
        }

        log.info("[Patch 迁移] 检测到 {} 个目标记忆仍有待处理 Patch，准备按当前链路执行全量合并。", targetIds.size());
        for (Long targetId : targetIds) {
            mergeTarget(targetId, "PATCH_TABLE_MIGRATION");
        }
    }

    private void submitMergeTask(Long targetMemoryId, String triggerSource) {
        if (!mergingTargets.add(targetMemoryId)) {
            log.info("[Patch 合并跳过] 目标记忆 ID {} 已在合并流程中，触发来源：{}。", targetMemoryId, triggerSource);
            return;
        }

        taskExecutor.execute(() -> {
            try {
                mergeTarget(targetMemoryId, triggerSource);
            } finally {
                mergingTargets.remove(targetMemoryId);
            }
        });
    }

    private void mergeTarget(Long targetMemoryId, String triggerSource) {
        List<MemoryPatchEntity> patches = patchRepo.findByTargetMemoryIdAndCompressedFalseOrderByCreatedAtAsc(targetMemoryId);
        if (patches.isEmpty()) {
            log.info("[Patch 合并跳过] 目标记忆 ID {} 当前没有待处理 Patch，触发来源：{}。", targetMemoryId, triggerSource);
            return;
        }

        MemoryArchiveEntity archive = archiveRepo.findById(targetMemoryId).orElse(null);
        if (archive == null) {
            log.warn("[Patch 悬空迁移] 目标记忆 ID {} 不存在，但检测到 {} 条待处理 Patch，转入悬空 Patch 迁移流程。", targetMemoryId, patches.size());
            handleOrphanPatches(targetMemoryId, patches);
            return;
        }

        int currentMergeCount = archive.getMergeCount() == null ? 0 : archive.getMergeCount();
        int maxMergeCount = props.getChatMemory().getPatchMergeMaxCount();

        log.info("[Patch 合并开始] 目标记忆 ID {}，触发来源：{}，Patch 数量：{}，当前合并次数：{}，上限：{}。",
                targetMemoryId, triggerSource, patches.size(), currentMergeCount, maxMergeCount);

        try {
            String patchText = buildPatchText(patches);
            boolean createNewArchive = currentMergeCount >= maxMergeCount;
            MemoryPatchMergeAiService.PatchSummaryResponse patchSummaryResponse;

            if (createNewArchive) {
                patchSummaryResponse = patchMergeAiService.summarizePatchAsNewArchive(
                        archive.getCategory(),
                        formatArchiveCreatedAt(archive),
                        archive.getDetailedSummary(),
                        patchText
                );
            } else {
                patchSummaryResponse = patchMergeAiService.mergePatchIntoArchive(
                        archive.getCategory(),
                        formatArchiveCreatedAt(archive),
                        archive.getDetailedSummary(),
                        patchText
                );
            }

            String normalizedDetailedSummary = normalize(patchSummaryResponse.getSummary());
            String keywordSummary = normalize(patchSummaryResponse.getNewKeywordSummary());

            if (normalizedDetailedSummary.isBlank()) {
                log.warn("[Patch 合并失败] 目标记忆 ID {} 的 AI 合并结果为空，保留原 Patch 等待下次处理。", targetMemoryId);
                return;
            }

            if (createNewArchive) {
                MemoryArchiveEntity newArchive = new MemoryArchiveEntity();
                newArchive.setSessionId(archive.getSessionId());
                newArchive.setCategory(archive.getCategory());
                newArchive.setKeywordSummary(keywordSummary);
                newArchive.setDetailedSummary(normalizedDetailedSummary);
                newArchive = archiveRepo.save(newArchive);
                embeddingService.indexNewMemoryArchive(newArchive);

                markPatchesCompressed(patches);
                log.info("[Patch 转新事件] 目标记忆 ID {} 已达到合并上限，当前 Patch 已整理为新记忆 ID {}。", targetMemoryId, newArchive.getId());
            } else {
                archive.setKeywordSummary(keywordSummary);
                archive.setDetailedSummary(normalizedDetailedSummary);
                archive.setMergeCount(currentMergeCount + 1);
                archiveRepo.save(archive);
                embeddingService.refreshMemoryArchiveEmbedding(archive);

                markPatchesCompressed(patches);
                log.info("[Patch 合并完成] 目标记忆 ID {} 已完成合并，本次合并后次数为 {}。", targetMemoryId, archive.getMergeCount());
            }
        } catch (Exception e) {
            log.error("[Patch 合并失败] 目标记忆 ID {} 的闭环处理异常。", targetMemoryId, e);
        }
    }

    private void markPatchesCompressed(List<MemoryPatchEntity> patches) {
        LocalDateTime compressedAt = LocalDateTime.now();
        patches.forEach(patch -> {
            patch.setCompressed(true);
            patch.setCompressedAt(compressedAt);
        });
        patchRepo.saveAll(patches);
    }

    private void handleOrphanPatches(Long targetMemoryId, List<MemoryPatchEntity> patches) {
        if (patches == null || patches.isEmpty()) {
            return;
        }

        if (patches.size() == 1) {
            MemoryPatchEntity patch = patches.getFirst();
            MemoryArchiveEntity newArchive = createArchiveFromPatch(targetMemoryId, patch);
            if (newArchive == null) {
                return;
            }
            embeddingService.indexNewMemoryArchive(newArchive);
            markPatchesCompressed(List.of(patch));
            log.info("[Patch 悬空迁移完成] 原目标记忆 ID {} 只有 1 条 Patch，已直接转存为新记忆 ID {}。", targetMemoryId, newArchive.getId());
            return;
        }

        MemoryPatchEntity basePatch = patches.getFirst();
        MemoryArchiveEntity baseArchive = createArchiveFromPatch(targetMemoryId, basePatch);
        if (baseArchive == null) {
            return;
        }
        embeddingService.indexNewMemoryArchive(baseArchive);
        markPatchesCompressed(List.of(basePatch));

        List<MemoryPatchEntity> remainingPatches = patches.stream()
                .skip(1)
                .toList();
        remainingPatches.forEach(patch -> patch.setTargetMemoryId(baseArchive.getId()));
        patchRepo.saveAll(remainingPatches);

        log.info("[Patch 悬空迁移完成] 原目标记忆 ID {} 的首条 Patch 已转为新记忆 ID {}，其余 {} 条 Patch 继续走追加流程。",
                targetMemoryId, baseArchive.getId(), remainingPatches.size());
        mergeTarget(baseArchive.getId(), "ORPHAN_PATCH_RECOVERY");
    }

    private MemoryArchiveEntity createArchiveFromPatch(Long targetMemoryId, MemoryPatchEntity patch) {
        ExtractedMemoryEventDTO seedEvent = extractArchiveSeedFromPatch(targetMemoryId, patch);
        if (seedEvent == null) {
            log.warn("[Patch 悬空迁移失败] 原目标记忆 ID {} 的 Patch ID {} 无法抽取出有效记忆事件，保留待处理状态。",
                    targetMemoryId, patch.getId());
            return null;
        }

        MemoryArchiveEntity archive = new MemoryArchiveEntity();
        archive.setCategory(normalize(seedEvent.getTopic()));
        archive.setKeywordSummary(seedEvent.getKeywordSummary());
        archive.setDetailedSummary(normalize(seedEvent.getNarrative()));
        return archiveRepo.save(archive);
    }

    private ExtractedMemoryEventDTO extractArchiveSeedFromPatch(Long targetMemoryId, MemoryPatchEntity patch) {
        String patchContent = normalize(patch.getCorrectionContent());
        if (patchContent.isBlank()) {
            return null;
        }

        try {
            FactExtractionAiService.FactExtractionResponse response = factExtractionAiService.extractAndMatchFacts(
                    List.of(UserMessage.from(patchContent)),
                    "无引用记忆"
            );

            List<ExtractedMemoryEventDTO> events = response.getEvents();
            if (events == null || events.isEmpty()) {
                log.warn("[Patch 悬空迁移失败] 原目标记忆 ID {} 的 Patch ID {} 未抽取到任何记忆事件。", targetMemoryId, patch.getId());
                return null;
            }

            ExtractedMemoryEventDTO event = events.getFirst();
            if (normalize(event.getTopic()).isBlank() || normalize(event.getNarrative()).isBlank()) {
                log.warn("[Patch 悬空迁移失败] 原目标记忆 ID {} 的 Patch ID {} 抽取结果缺少 topic 或 narrative。", targetMemoryId, patch.getId());
                return null;
            }
            return event;
        } catch (Exception e) {
            log.error("[Patch 悬空迁移失败] 原目标记忆 ID {} 的 Patch ID {} 在抽取 archive 种子时发生异常。", targetMemoryId, patch.getId(), e);
            return null;
        }
    }

    private String buildPatchText(List<MemoryPatchEntity> patches) {
        return patches.stream()
                .map(patch -> {
                    String correction = normalize(patch.getCorrectionContent());
                    if (correction.isBlank()) {
                        return null;
                    }

                    String createdAt = patch.getCreatedAt() == null
                            ? "未知时间"
                            : patch.getCreatedAt().format(PATCH_TIME_FORMATTER);
                    return "- [" + createdAt + "] " + correction;
                })
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private String formatArchiveCreatedAt(MemoryArchiveEntity archive) {
        if (archive == null || archive.getCreatedAt() == null) {
            return "未知时间";
        }
        return archive.getCreatedAt().format(ARCHIVE_TIME_FORMATTER);
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
