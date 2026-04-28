package p1.infrastructure.markdown;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p1.infrastructure.markdown.io.MarkdownFrontmatterIO;
import p1.infrastructure.markdown.model.MarkdownDocument;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class MarkdownFileAccess {

    private final MarkdownFrontmatterIO frontmatterIO;

    public Optional<MarkdownDocument> readOptional(Path path, String target) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            return Optional.of(frontmatterIO.read(path));
        } catch (IOException e) {
            throw failure("read", target, e);
        }
    }

    public void write(Path path, MarkdownDocument document, String target) {
        try {
            frontmatterIO.write(path, document);
        } catch (IOException e) {
            throw failure("write", target, e);
        }
    }

    public void createIfMissing(Path path, MarkdownDocument document, String target) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    path,
                    frontmatterIO.build(document),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
        } catch (FileAlreadyExistsException ignored) {
            // Another writer already created the note between existence check and write.
        } catch (IOException e) {
            throw failure("create", target, e);
        }
    }

    public void append(Path path, String content, String target) {
        try {
            Files.writeString(
                    path,
                    content,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw failure("append", target, e);
        }
    }

    public void deleteIfExists(Path path, String target) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw failure("delete", target, e);
        }
    }

    public List<Path> listRegularFiles(Path rootDirectory, String target) {
        if (!Files.isDirectory(rootDirectory)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(rootDirectory)) {
            return stream.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw failure("scan", target, e);
        }
    }

    private IllegalStateException failure(String action, String target, IOException cause) {
        return new IllegalStateException("failed to " + action + " " + target, cause);
    }
}
