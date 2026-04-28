package p1.service.markdown;

import p1.infrastructure.markdown.model.MarkdownDocument;

import java.time.LocalDate;
import java.util.Optional;

public interface RawDialogueStore {

    Optional<MarkdownDocument> findDailyNote(String sessionId, LocalDate date);

    void createDailyNoteIfMissing(String sessionId, LocalDate date, MarkdownDocument document);

    void appendToDailyNote(String sessionId, LocalDate date, String messageBlock);

    String relativeDailyNotePath(String sessionId, LocalDate date);
}
