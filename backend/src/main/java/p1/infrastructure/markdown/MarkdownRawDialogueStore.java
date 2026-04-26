package p1.infrastructure.markdown;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p1.config.prop.AssistantProperties;
import p1.infrastructure.markdown.model.MarkdownDocument;
import p1.service.markdown.RawDialogueStore;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MarkdownRawDialogueStore implements RawDialogueStore {

    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AssistantProperties props;
    private final MarkdownFileAccess fileAccess;

    @Override
    public Optional<MarkdownDocument> findDailyNote(String sessionId, LocalDate date) {
        Path notePath = resolveDailyNotePath(sessionId, date);
        return fileAccess.readOptional(notePath, "raw dialogue markdown");
    }

    @Override
    public void createDailyNoteIfMissing(String sessionId, LocalDate date, MarkdownDocument document) {
        Path notePath = resolveDailyNotePath(sessionId, date);
        fileAccess.createIfMissing(notePath, document, "raw dialogue markdown");
    }

    @Override
    public void appendToDailyNote(String sessionId, LocalDate date, String messageBlock) {
        Path notePath = resolveDailyNotePath(sessionId, date);
        fileAccess.append(notePath, messageBlock, "raw dialogue markdown");
    }

    private Path resolveDailyNotePath(String sessionId, LocalDate date) {
        return Paths.get(props.getMdRepository().getPath())
                .resolve(relativeDailyNotePath(sessionId, date) + ".md");
    }

    @Override
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
