package p1.repo.markdown;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import p1.config.prop.AssistantProperties;
import p1.repo.markdown.io.MarkdownYamlIO;
import p1.repo.markdown.model.MarkdownDocument;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RawMdRepository {

    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AssistantProperties props;
    private final MarkdownYamlIO markdownYamlIO;

    public Optional<MarkdownDocument> findDailyNote(String sessionId, LocalDate date) {
        Path notePath = resolveDailyNotePath(sessionId, date);
        if (!Files.exists(notePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(markdownYamlIO.read(notePath));
        } catch (IOException e) {
            throw new IllegalStateException("failed to read raw dialogue markdown", e);
        }
    }

    /**
     * raw daily note 只在第一次写入当天消息时创建，后续不再整文件回写。
     */
    public void createDailyNoteIfMissing(String sessionId, LocalDate date, MarkdownDocument document) {
        Path notePath = resolveDailyNotePath(sessionId, date);
        try {
            Files.createDirectories(notePath.getParent());
            Files.writeString(
                    notePath,
                    markdownYamlIO.build(document),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
        } catch (FileAlreadyExistsException ignored) {
            // 同一份 daily note 已存在时直接复用，不需要重复创建。
        } catch (IOException e) {
            throw new IllegalStateException("failed to create raw dialogue markdown", e);
        }
    }

    /**
     * raw 正文现在是追加式写入，避免随着文件变大而反复整文件读写。
     */
    public void appendToDailyNote(String sessionId, LocalDate date, String messageBlock) {
        Path notePath = resolveDailyNotePath(sessionId, date);
        try {
            Files.writeString(
                    notePath,
                    messageBlock,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new IllegalStateException("failed to append raw dialogue markdown", e);
        }
    }

    private Path resolveDailyNotePath(String sessionId, LocalDate date) {
        return Paths.get(props.getMdRepository().getPath())
                .resolve(relativeDailyNotePath(sessionId, date) + ".md");
    }

    public String relativeDailyNotePath(String sessionId, LocalDate date) {
        return Paths.get(
                "sessions",
                sessionId,
                "raw",
                "dialogues",
                YEAR_FORMATTER.format(date),
                YEAR_MONTH_FORMATTER.format(date),
                DATE_FORMATTER.format(date)
        ).toString().replace('\\', '/');
    }
}
