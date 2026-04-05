package p1.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p1.component.ai.memory.MemoryPatchMergeCoordinator;
import p1.model.MemoryArchiveEntity;
import p1.model.MemoryPatchEntity;
import p1.repo.MemoryArchiveRepository;
import p1.repo.MemoryPatchRepository;

@Service
@AllArgsConstructor
@Slf4j
public class MemoryWriteService {

    private final MemoryArchiveRepository archiveRepo;
    private final MemoryPatchRepository patchRepo;
    private final EmbeddingService embeddingService;
    private final MemoryPatchMergeCoordinator patchMergeCoordinator;

    public void saveNewMemory(String category, String keywordSummary, String detailedSummary) {
        log.info("[记忆写入] 尝试保存新记忆，类别：{}，关键词摘要：{}，详细摘要：{}", category, keywordSummary, detailedSummary);
        try {
            MemoryArchiveEntity archive = new MemoryArchiveEntity();
            archive.setCategory(normalize(category));
            archive.setKeywordSummary(normalize(keywordSummary));
            archive.setDetailedSummary(normalize(detailedSummary));
            archive = archiveRepo.save(archive);
            embeddingService.indexNewMemoryArchive(archive);

            log.info("[记忆写入] 新记忆已写入数据库，ID：{}，类别：{}", archive.getId(), archive.getCategory());
        } catch (Exception e) {
            log.error("[记忆写入失败] 保存新记忆时发生异常，类别：{}，关键词摘要：{}，详细摘要：{}", category, keywordSummary, detailedSummary, e);
        }
    }

    public void patchMemory(Long targetId, String correction) {
        log.info("[记忆 Patch] 尝试为记忆 ID {} 追加 Patch，内容：{}", targetId, correction);
        try {
            if (!archiveRepo.existsById(targetId)) {
                log.warn("[记忆 Patch 失败] 目标记忆 ID {} 不存在。", targetId);
                return;
            }

            MemoryPatchEntity patch = new MemoryPatchEntity();
            patch.setTargetMemoryId(targetId);
            patch.setCorrectionContent(correction);
            patchRepo.save(patch);

            log.info("[记忆 Patch] Patch 已保存，目标记忆 ID {}。", targetId);
            patchMergeCoordinator.onPatchSaved(targetId);
        } catch (Exception e) {
            log.error("[记忆 Patch 失败] 保存 Patch 时发生异常。", e);
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
