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
import p1.model.MemoryArchiveDocument;
import p1.model.MemoryPatchDocument;
import p1.service.EmbeddingService;
import p1.service.markdown.MemoryArchiveMarkdownService;
import p1.service.markdown.MemoryPatchMarkdownService;
import p1.utils.SessionUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
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

    private final MemoryArchiveMarkdownService archiveService;
    private final MemoryPatchMarkdownService patchService;
    private final FactExtractionAiService factExtractionAiService;
    private final MemoryPatchMergeAiService patchMergeAiService;
    private final EmbeddingService embeddingService;
    private final AssistantProperties props;
    private final Executor taskExecutor;

    private final Set<Long> mergingTargets = ConcurrentHashMap.newKeySet();

    public MemoryPatchMergeCoordinator(MemoryArchiveMarkdownService archiveService,
                                       MemoryPatchMarkdownService patchService,
                                       FactExtractionAiService factExtractionAiService,
                                       MemoryPatchMergeAiService patchMergeAiService,
                                       EmbeddingService embeddingService,
                                       AssistantProperties props,
                                       @Qualifier("asyncTaskExecutor") Executor taskExecutor) {
        this.archiveService = archiveService;
        this.patchService = patchService;
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

        long patchCount = patchService.countByTargetMemoryIdAndCompressedFalse(targetMemoryId);
        int threshold = props.getChatMemory().getPatchMergeThreshold();

        log.info("[Patch 合并检测] targetMemoryId={}，当前未压缩 patch 数量={}", targetMemoryId, patchCount);
        if (patchCount >= threshold) {
            log.info("[Patch 合并触发] targetMemoryId={} 达到阈值 {}", targetMemoryId, threshold);
            submitMergeTask(targetMemoryId, "PATCH_THRESHOLD");
        }
    }

    @Scheduled(
            initialDelayString = "${assistant.chat-memory.patch-merge-scan-fixed-delay-ms:300000}",
            fixedDelayString = "${assistant.chat-memory.patch-merge-scan-fixed-delay-ms:1800000}"
    )
    public void scanPendingPatches() {
        List<Long> targetIds = patchService.findDistinctTargetMemoryIdsByCompressedFalse();
        if (targetIds.isEmpty()) {
            return;
        }

        log.info("[Patch 定时扫描] 检测到 {} 个目标记忆存在未压缩 patch", targetIds.size());
        for (Long targetId : targetIds) {
            submitMergeTask(targetId, "SCHEDULED_SCAN");
        }
    }

    public void migrateAllPendingPatches() {
        List<Long> targetIds = patchService.findDistinctTargetMemoryIdsByCompressedFalse();
        if (targetIds.isEmpty()) {
            return;
        }

        log.info("[Patch 迁移] 检测到 {} 个目标记忆仍有待处理 patch，开始按当前链路执行全量合并", targetIds.size());
        for (Long targetId : targetIds) {
            mergeTarget(targetId, "PATCH_TABLE_MIGRATION");
        }
    }

    private void submitMergeTask(Long targetMemoryId, String triggerSource) {
        if (!mergingTargets.add(targetMemoryId)) {
            log.info("[Patch 合并跳过] targetMemoryId={} 已在合并流程中，triggerSource={}",
                    targetMemoryId, triggerSource);
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
        List<MemoryPatchDocument> patches = patchService.findByTargetMemoryIdAndCompressedFalseOrderByCreatedAtAsc(targetMemoryId);
        if (patches.isEmpty()) {
            log.info("[Patch 合并跳过] targetMemoryId={} 当前没有待处理 patch，triggerSource={}",
                    targetMemoryId, triggerSource);
            return;
        }

        MemoryArchiveDocument archive = archiveService.findById(targetMemoryId).orElse(null);
        if (archive == null) {
            log.warn("[Patch 悬空迁移] targetMemoryId={} 对应记忆不存在，但检测到 {} 条待处理 patch",
                    targetMemoryId, patches.size());
            handleOrphanPatches(targetMemoryId, patches);
            return;
        }

        int currentMergeCount = archive.getMergeCount() == null ? 0 : archive.getMergeCount();
        int maxMergeCount = props.getChatMemory().getPatchMergeMaxCount();

        log.info("[Patch 合并开始] targetMemoryId={}，triggerSource={}，patchCount={}，mergeCount={}，maxMergeCount={}",
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
                log.warn("[Patch 合并失败] targetMemoryId={} 的 AI 合并结果为空，保留原 patch", targetMemoryId);
                return;
            }

            if (createNewArchive) {
                MemoryArchiveDocument newArchive = archiveService.create(
                        archive.getSessionId(),
                        archive.getCategory(),
                        keywordSummary,
                        normalizedDetailedSummary
                );
                embeddingService.indexNewMemoryArchive(newArchive);

                markPatchesCompressed(patches);
                log.info("[Patch 转新记忆] targetMemoryId={} 已达到合并上限，生成新记忆 memoryId={}",
                        targetMemoryId, newArchive.getId());
            } else {
                archive.setKeywordSummary(keywordSummary);
                archive.setDetailedSummary(normalizedDetailedSummary);
                archive.setMergeCount(currentMergeCount + 1);
                archive = archiveService.save(archive);
                embeddingService.refreshMemoryArchiveEmbedding(archive);

                markPatchesCompressed(patches);
                log.info("[Patch 合并完成] targetMemoryId={} 合并完成，最新 mergeCount={}",
                        targetMemoryId, archive.getMergeCount());
            }
        } catch (Exception e) {
            log.error("[Patch 合并失败] targetMemoryId={} 合并异常", targetMemoryId, e);
        }
    }

    private void markPatchesCompressed(List<MemoryPatchDocument> patches) {
        LocalDateTime compressedAt = LocalDateTime.now();
        patches.forEach(patch -> {
            patch.setCompressed(true);
            patch.setCompressedAt(compressedAt);
        });
        patchService.saveAll(patches);
    }

    private void handleOrphanPatches(Long targetMemoryId, List<MemoryPatchDocument> patches) {
        if (patches == null || patches.isEmpty()) {
            return;
        }

        if (patches.size() == 1) {
            MemoryPatchDocument patch = patches.getFirst();
            MemoryArchiveDocument newArchive = createArchiveFromPatch(targetMemoryId, patch);
            if (newArchive == null) {
                return;
            }
            embeddingService.indexNewMemoryArchive(newArchive);
            markPatchesCompressed(List.of(patch));
            log.info("[Patch 悬空迁移完成] 原 targetMemoryId={} 只有 1 条 patch，已转存为新记忆 memoryId={}",
                    targetMemoryId, newArchive.getId());
            return;
        }

        MemoryPatchDocument basePatch = patches.getFirst();
        MemoryArchiveDocument baseArchive = createArchiveFromPatch(targetMemoryId, basePatch);
        if (baseArchive == null) {
            return;
        }
        embeddingService.indexNewMemoryArchive(baseArchive);
        markPatchesCompressed(List.of(basePatch));

        List<MemoryPatchDocument> remainingPatches = patches.stream()
                .skip(1)
                .peek(patch -> patch.setTargetMemoryId(baseArchive.getId()))
                .toList();
        patchService.saveAll(remainingPatches);

        log.info("[Patch 悬空迁移完成] 原 targetMemoryId={} 的首条 patch 已转成新记忆 memoryId={}，其余 {} 条 patch 已改挂新记忆",
                targetMemoryId, baseArchive.getId(), remainingPatches.size());
        mergeTarget(baseArchive.getId(), "ORPHAN_PATCH_RECOVERY");
    }

    private MemoryArchiveDocument createArchiveFromPatch(Long targetMemoryId, MemoryPatchDocument patch) {
        ExtractedMemoryEventDTO seedEvent = extractArchiveSeedFromPatch(targetMemoryId, patch);
        if (seedEvent == null) {
            log.warn("[Patch 悬空迁移失败] targetMemoryId={}，patchId={} 无法提取有效记忆事件",
                    targetMemoryId, patch.getId());
            return null;
        }

        return archiveService.create(
                SessionUtil.normalizeSessionId(null),
                normalize(seedEvent.getTopic()),
                normalize(seedEvent.getKeywordSummary()),
                normalize(seedEvent.getNarrative())
        );
    }

    private ExtractedMemoryEventDTO extractArchiveSeedFromPatch(Long targetMemoryId, MemoryPatchDocument patch) {
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
                log.warn("[Patch 悬空迁移失败] targetMemoryId={}，patchId={} 未抽取到任何记忆事件",
                        targetMemoryId, patch.getId());
                return null;
            }

            ExtractedMemoryEventDTO event = events.getFirst();
            if (normalize(event.getTopic()).isBlank() || normalize(event.getNarrative()).isBlank()) {
                log.warn("[Patch 悬空迁移失败] targetMemoryId={}，patchId={} 的抽取结果缺少 topic 或 narrative",
                        targetMemoryId, patch.getId());
                return null;
            }
            return event;
        } catch (Exception e) {
            log.error("[Patch 悬空迁移失败] targetMemoryId={}，patchId={} 在提取 archive 种子时发生异常",
                    targetMemoryId, patch.getId(), e);
            return null;
        }
    }

    private String buildPatchText(List<MemoryPatchDocument> patches) {
        return patches.stream()
                .filter(patch -> patch.getCreatedAt() != null)
                .sorted(Comparator.comparing(MemoryPatchDocument::getCreatedAt))
                .map(patch -> {
                    String correction = normalize(patch.getCorrectionContent());
                    if (correction.isBlank()) {
                        return null;
                    }
                    String createdAt = patch.getCreatedAt().format(PATCH_TIME_FORMATTER);
                    return "- [" + createdAt + "] " + correction;
                })
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private String formatArchiveCreatedAt(MemoryArchiveDocument archive) {
        if (archive == null || archive.getCreatedAt() == null) {
            return "未知时间";
        }
        return archive.getCreatedAt().format(ARCHIVE_TIME_FORMATTER);
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
