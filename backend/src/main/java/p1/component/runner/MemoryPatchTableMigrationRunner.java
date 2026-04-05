package p1.component.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import p1.component.ai.memory.MemoryPatchMergeCoordinator;
import p1.repo.MemoryPatchRepository;

//@Component
@RequiredArgsConstructor
@Slf4j
public class MemoryPatchTableMigrationRunner {

    private final MemoryPatchRepository patchRepository;
    private final MemoryPatchMergeCoordinator patchMergeCoordinator;

    @EventListener(ApplicationReadyEvent.class)
    public void migratePatchTable() {
        long patchCount = patchRepository.countByCompressedFalse();
        if (patchCount == 0) {
            return;
        }

        // 一次性迁移入口：将 patch 表中尚未压缩的记录按当前闭环链路压回 archive
        log.info("[Patch 表迁移] 检测到 {} 条未压缩历史 Patch，准备执行一次性迁移。", patchCount);
        patchMergeCoordinator.migrateAllPendingPatches();
    }
}
