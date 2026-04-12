package p1.repo.markdown;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import p1.config.prop.AssistantProperties;
import p1.repo.markdown.io.MarkdownFrontmatterIO;
import p1.repo.markdown.model.MarkdownDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
public class MemoryArchiveMarkdownRepository {

    private final AssistantProperties props;
    private final MarkdownFrontmatterIO markdownFrontmatterIO;

    public Optional<MarkdownDocument> find(Path path) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            return Optional.of(markdownFrontmatterIO.read(path));
        } catch (IOException e) {
            throw new IllegalStateException("failed to read memory archive markdown", e);
        }
    }

    public void save(Path path, MarkdownDocument document) {
        try {
            markdownFrontmatterIO.write(path, document);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write memory archive markdown", e);
        }
    }

    public void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalStateException("failed to delete memory archive markdown", e);
        }
    }

    public List<Path> listAllPaths() {
        Path root = Paths.get(props.getMdRepository().getPath());
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().replace('\\', '/').contains("/wiki/memories/"))
                    .forEach(result::add);
        } catch (IOException e) {
            throw new IllegalStateException("failed to scan memory archive markdown files", e);
        }
        return result;
    }
}
