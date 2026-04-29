package p1.infrastructure.markdown;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p1.config.prop.AssistantProperties;
import p1.infrastructure.markdown.assembler.RawBatchMdAssembler;
import p1.infrastructure.markdown.model.RawBatchDocument;
import p1.service.markdown.DialogueBatchStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class MarkdownDialogueBatchStore implements DialogueBatchStore {

    private final AssistantProperties props;
    private final MarkdownFileAccess fileAccess;
    private final RawBatchMdAssembler rawBatchMdAssembler;

    @Override
    public Optional<RawBatchDocument> findCollecting(String sessionId) {
        return fileAccess.readOptional(collectingPath(sessionId), "dialogue batch markdown")
                .map(rawBatchMdAssembler::fromMarkdown);
    }

    @Override
    public Optional<RawBatchDocument> findProcessing(String sessionId) {
        return fileAccess.readOptional(processingPath(sessionId), "dialogue batch markdown")
                .map(rawBatchMdAssembler::fromMarkdown);
    }

    @Override
    public void saveCollecting(String sessionId, RawBatchDocument document) {
        fileAccess.write(collectingPath(sessionId), rawBatchMdAssembler.toMarkdown(document), "dialogue batch markdown");
    }

    @Override
    public void saveProcessing(String sessionId, RawBatchDocument document) {
        fileAccess.write(processingPath(sessionId), rawBatchMdAssembler.toMarkdown(document), "dialogue batch markdown");
    }

    @Override
    public void deleteCollecting(String sessionId) {
        fileAccess.deleteIfExists(collectingPath(sessionId), "dialogue batch markdown");
    }

    @Override
    public void deleteProcessing(String sessionId) {
        fileAccess.deleteIfExists(processingPath(sessionId), "dialogue batch markdown");
    }

    @Override
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

    private Path collectingPath(String sessionId) {
        return batchRoot(sessionId).resolve("collecting.md");
    }

    private Path processingPath(String sessionId) {
        return batchRoot(sessionId).resolve("processing.md");
    }

    private Path batchRoot(String sessionId) {
        return Paths.get(props.getMdRepository().getPath(), "_system", "sessions", sessionId, "dialogue-batch");
    }

}
