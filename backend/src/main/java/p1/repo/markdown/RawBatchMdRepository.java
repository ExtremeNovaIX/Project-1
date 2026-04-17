package p1.repo.markdown;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import p1.config.prop.AssistantProperties;
import p1.repo.markdown.io.MarkdownYamlIO;
import p1.repo.markdown.model.MarkdownDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 用于管理对话批次信息的repo，路径为backend\data\memory\_system\sessions\{sessionId}\dialogue-batch\
 * 用于存储对话的处理情况，用于意外关闭后的对话恢复。
 * 管理的文件为：
 * - collecting.md：表示当前“正在收集的、还没真正送去压缩的累计消息池”
 * - processing.md：当前“已经转入压缩流程、当前正在处理的那一批消息”
 */
@Repository
@RequiredArgsConstructor
public class RawBatchMdRepository {

    private final AssistantProperties props;
    private final MarkdownYamlIO markdownYamlIO;

    /**
     * 读取 collecting.md。
     * 它表示“正在收集的、还没真正送去压缩的累计消息池”。
     */
    public Optional<MarkdownDocument> findCollecting(String sessionId) {
        return read(collectingPath(sessionId));
    }

    /**
     * 读取 processing.md。
     * 它表示“已经转入压缩流程、当前正在处理的那一批消息”。
     */
    public Optional<MarkdownDocument> findProcessing(String sessionId) {
        return read(processingPath(sessionId));
    }

    public void saveCollecting(String sessionId, MarkdownDocument document) {
        write(collectingPath(sessionId), document);
    }

    public void saveProcessing(String sessionId, MarkdownDocument document) {
        write(processingPath(sessionId), document);
    }

    public void deleteCollecting(String sessionId) {
        deleteIfExists(collectingPath(sessionId));
    }

    public void deleteProcessing(String sessionId) {
        deleteIfExists(processingPath(sessionId));
    }

    /**
     * 列出所有还存在未完成 batch 文件的 session。
     * 只要一个 session 下还保留 collecting.md 或 processing.md，
     * 就说明它还有待处理或待恢复的对话批次。
     */
    public Set<String> listSessionIdsWithOpenBatches() {
        Path sessionsRoot = Paths.get(props.getMdRepository().getPath(), "_system", "sessions");
        if (!Files.isDirectory(sessionsRoot)) {
            return Set.of();
        }

        try (Stream<Path> stream = Files.list(sessionsRoot)) {
            return stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(sessionId -> Files.exists(collectingPath(sessionId)) || Files.exists(processingPath(sessionId)))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException e) {
            throw new IllegalStateException("failed to list session ids with open dialogue batches", e);
        }
    }

    private Optional<MarkdownDocument> read(Path path) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            return Optional.of(markdownYamlIO.read(path));
        } catch (IOException e) {
            throw new IllegalStateException("failed to read dialogue batch markdown", e);
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalStateException("failed to delete dialogue batch markdown", e);
        }
    }

    private Path collectingPath(String sessionId) {
        return batchRoot(sessionId).resolve("collecting.md");
    }

    private Path processingPath(String sessionId) {
        return batchRoot(sessionId).resolve("processing.md");
    }

    private Path batchRoot(String sessionId) {
        return Paths.get(props.getMdRepository().getPath(), "_system", "sessions", sessionId, "dialogue-batch");
    }

    private void write(Path path, MarkdownDocument document) {
        try {
            markdownYamlIO.write(path, document);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write dialogue batch markdown", e);
        }
    }
}
