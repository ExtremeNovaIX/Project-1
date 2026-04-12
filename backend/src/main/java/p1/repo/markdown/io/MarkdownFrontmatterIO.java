package p1.repo.markdown.io;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import p1.repo.markdown.model.MarkdownDocument;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MarkdownFrontmatterIO {

    public MarkdownDocument read(Path path) throws IOException {
        String content = Files.readString(path).replace("\r\n", "\n").replace('\r', '\n');
        if (!content.startsWith("---\n")) {
            return new MarkdownDocument(new LinkedHashMap<>(), content);
        }

        int frontmatterEnd = content.indexOf("\n---\n", 4);
        if (frontmatterEnd < 0) {
            return new MarkdownDocument(new LinkedHashMap<>(), content);
        }


        String yamlText = content.substring(4, frontmatterEnd);
        String body = content.substring(frontmatterEnd + 5);

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
        Files.createDirectories(path.getParent());
        String content = build(frontmatter, body);

        Path tempPath = Files.createTempFile(path.getParent(), path.getFileName().toString() + ".", ".tmp");
        try {
            Files.writeString(tempPath, content);
            try {
                Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempPath);
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
}
