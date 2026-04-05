package p1.component.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import p1.service.EmbeddingService;

@Component
@RequiredArgsConstructor
@Slf4j
public class LuceneRebuildScriptRunner {

    private final Environment environment;
    private final EmbeddingService embeddingService;

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildLuceneIndex() {
        boolean enabled = false;
        if (!enabled) {
            return;
        }

        log.info("[Lucene 重建脚本] 检测到 LUCENE_REBUILD_ENABLED=true，准备重建记忆向量索引。");
        try {
            embeddingService.rebuildAllMemoryArchiveEmbeddings();
            log.info("[Lucene 重建脚本] 记忆向量索引重建完成。");
        } catch (Exception e) {
            log.error("[Lucene 重建脚本失败] 重建记忆向量索引时发生异常。", e);
        }
    }
}
