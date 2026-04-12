package p1.repo.markdown;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import p1.config.prop.AssistantProperties;
import p1.repo.markdown.io.MarkdownFrontmatterIO;
import p1.repo.markdown.model.MarkdownDocument;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RawDialogueMarkdownRepository {

    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private final AssistantProperties props;
    private final MarkdownFrontmatterIO markdownFrontmatterIO;

    /**
     * 根据会话 ID 和日期在raw中查询对应的 Markdown DailyNote文件
     */
    public Optional<MarkdownDocument> findDailyNote(String sessionId, LocalDate date) {
        Path notePath = resolveDailyNotePath(sessionId, date);
        if (!notePath.toFile().exists()) {
            return Optional.empty();
        }
        try {
            return Optional.of(markdownFrontmatterIO.read(notePath));
        } catch (IOException e) {
            throw new IllegalStateException("failed to read raw dialogue markdown", e);
        }
    }

    public void saveDailyNote(String sessionId, LocalDate date, MarkdownDocument document) {
        try {
            markdownFrontmatterIO.write(resolveDailyNotePath(sessionId, date), document);
        } catch (IOException e) {
            throw new IllegalStateException("failed to save raw dialogue markdown", e);
        }
    }

    /**
     * 解析会话 ID 和日期，返回raw中对应的 Markdown 文件路径
     */
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
