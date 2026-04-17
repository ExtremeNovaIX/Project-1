package p1.repo.markdown;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import p1.repo.markdown.io.MarkdownYamlIO;
import p1.repo.markdown.model.MarkdownDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
public class RecentEventGroupMarkdownRepository {

    private final MarkdownYamlIO markdownYamlIO;

    public Optional<MarkdownDocument> find(Path path) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            return Optional.of(markdownYamlIO.read(path));
        } catch (IOException e) {
            throw new IllegalStateException("failed to read recent event group markdown", e);
        }
    }

    public void save(Path path, MarkdownDocument document) {
        try {
            markdownYamlIO.write(path, document);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write recent event group markdown", e);
        }
    }

    public void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalStateException("failed to delete recent event group markdown", e);
        }
    }

    public List<Path> listAllPaths(Path rootDirectory) {
        if (!Files.isDirectory(rootDirectory)) {
            return List.of();
        }

        List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(rootDirectory)) {
            stream.filter(Files::isRegularFile)
                    .forEach(result::add);
        } catch (IOException e) {
            throw new IllegalStateException("failed to scan recent event group markdown files", e);
        }
        return result;
    }
}
