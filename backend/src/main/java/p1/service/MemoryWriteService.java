package p1.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p1.component.ai.memory.MemoryPatchMergeCoordinator;
import p1.model.MemoryArchiveDocument;
import p1.model.MemoryPatchDocument;
import p1.service.markdown.MemoryArchiveMarkdownService;
import p1.service.markdown.MemoryPatchMarkdownService;

@Service
@AllArgsConstructor
@Slf4j
public class MemoryWriteService {

    private final MemoryArchiveMarkdownService archiveService;
    private final MemoryPatchMarkdownService patchService;
    private final EmbeddingService embeddingService;
    private final MemoryPatchMergeCoordinator patchMergeCoordinator;

    public void saveNewMemory(String sessionId, String category, String keywordSummary, String detailedSummary) {
        log.info("[记忆写入] 尝试保存新记忆，sessionId={}，category={}，keywordSummary={}",
                sessionId, category, keywordSummary);
        try {
            MemoryArchiveDocument archive = archiveService.create(
                    sessionId,
                    normalize(category),
                    normalize(keywordSummary),
                    normalize(detailedSummary)
            );
            embeddingService.indexNewMemoryArchive(archive);

            log.info("[记忆写入] 新记忆已写入 markdown，memoryId={}，category={}",
                    archive.getId(), archive.getCategory());
        } catch (Exception e) {
            log.error("[记忆写入失败] 保存新记忆时发生异常，sessionId={}，category={}，keywordSummary={}",
                    sessionId, category, keywordSummary, e);
        }
    }

    public void patchMemory(Long targetId, String correction) {
        log.info("[记忆 Patch] 尝试为记忆 ID {} 追加 patch", targetId);
        try {
            if (!archiveService.existsById(targetId)) {
                log.warn("[记忆 Patch 失败] 目标记忆 ID {} 不存在", targetId);
                return;
            }

            MemoryPatchDocument patch = new MemoryPatchDocument();
            patch.setTargetMemoryId(targetId);
            patch.setCorrectionContent(normalize(correction));
            patch = patchService.save(patch);

            log.info("[记忆 Patch] Patch 已写入 markdown，patchId={}，targetMemoryId={}",
                    patch.getId(), targetId);
            patchMergeCoordinator.onPatchSaved(targetId);
        } catch (Exception e) {
            log.error("[记忆 Patch 失败] 保存 patch 时发生异常，targetMemoryId={}", targetId, e);
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
