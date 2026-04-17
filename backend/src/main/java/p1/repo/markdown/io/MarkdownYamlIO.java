package p1.repo.markdown.io;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import p1.repo.markdown.model.MarkdownDocument;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MarkdownYamlIO {

    private static final int MAX_WRITE_ATTEMPTS = 3;
    private static final long WRITE_RETRY_DELAY_MS = 100L;

    public MarkdownDocument read(Path path) throws IOException {
        String content = Files.readString(path).replace("\r\n", "\n").replace('\r', '\n');
        if (!content.startsWith("---\n")) {
            return new MarkdownDocument(new LinkedHashMap<>(), content);
        }

        int yamlEnd = content.indexOf("\n---\n", 4);
        if (yamlEnd < 0) {
            return new MarkdownDocument(new LinkedHashMap<>(), content);
        }

        String yamlText = content.substring(4, yamlEnd);
        String body = content.substring(yamlEnd + 5);

        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object loaded = yaml.load(yamlText);
        Map<String, Object> frontmatter = loaded instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        return new MarkdownDocument(frontmatter, body);
    }

    public void write(Path path, MarkdownDocument document) throws IOException {
        write(path, document.frontmatter(), document.body());
    }

    public void write(Path path, Map<String, Object> frontmatter, String body) throws IOException {
        Path normalizedPath = path.toAbsolutePath().normalize();
        String content = build(frontmatter, body);

        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            Path tempPath = null;
            try {
                createParentDirectories(normalizedPath);
                tempPath = createTempPath(normalizedPath);
                writeTempFile(tempPath, content);
                moveIntoPlace(tempPath, normalizedPath);
                return;
            } catch (AccessDeniedException | NoSuchFileException e) {
                if (attempt == MAX_WRITE_ATTEMPTS) {
                    throw e;
                }
                pauseBeforeRetry();
            } finally {
                if (tempPath != null) {
                    deleteTempFile(tempPath);
                }
            }
        }
    }

    protected void createParentDirectories(Path path) throws IOException {
        Files.createDirectories(parentDirectory(path));
    }

    protected Path createTempPath(Path path) throws IOException {
        return Files.createTempFile(parentDirectory(path), path.getFileName().toString() + ".", ".tmp");
    }

    protected void writeTempFile(Path tempPath, String content) throws IOException {
        Files.writeString(tempPath, content);
    }

    protected void moveIntoPlace(Path tempPath, Path path) throws IOException {
        try {
            moveAtomically(tempPath, path);
        } catch (AtomicMoveNotSupportedException ignored) {
            moveReplaceExisting(tempPath, path);
        }
    }

    protected void moveAtomically(Path tempPath, Path path) throws IOException {
        Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    protected void moveReplaceExisting(Path tempPath, Path path) throws IOException {
        Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
    }

    protected void deleteTempFile(Path tempPath) throws IOException {
        Files.deleteIfExists(tempPath);
    }

    protected void pauseBeforeRetry() throws IOException {
        try {
            Thread.sleep(WRITE_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("markdown write retry interrupted", e);
        }
    }

    public String build(MarkdownDocument document) {
        return build(document.frontmatter(), document.body());
    }

    public String build(Map<String, Object> frontmatter, String body) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setSplitLines(false);
        options.setIndent(2);

        Yaml yaml = new Yaml(options);
        StringWriter writer = new StringWriter();
        yaml.dump(frontmatter, writer);
        return "---\n" + writer.toString().trim() + "\n---\n\n" + body.stripTrailing() + "\n";
    }

    private Path parentDirectory(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent == null) {
            throw new IOException("markdown path must have a parent directory: " + path);
        }
        return parent;
    }
}
