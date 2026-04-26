package p1.repo.markdown.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import p1.infrastructure.markdown.io.MarkdownFrontmatterIO;
import p1.infrastructure.markdown.model.MarkdownDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownFrontmatterIOTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRetryWhenFirstMoveThrowsNoSuchFileException() throws Exception {
        RetryingMarkdownFrontmatterIO io = new RetryingMarkdownFrontmatterIO();
        Path path = tempDir.resolve("data/memory/_system/sessions/story-replay/dialogue-batch/collecting.md");

        io.write(path, new MarkdownDocument(Map.of("status", "collecting"), "body"));

        assertEquals(2, io.atomicMoveAttempts.get());
        assertTrue(Files.exists(path));
        assertTrue(io.read(path).body().contains("body"));
    }

    private static final class RetryingMarkdownFrontmatterIO extends MarkdownFrontmatterIO {

        private final AtomicInteger atomicMoveAttempts = new AtomicInteger();

        @Override
        protected void moveAtomically(Path tempPath, Path path) throws IOException {
            if (atomicMoveAttempts.incrementAndGet() == 1) {
                throw new NoSuchFileException(tempPath.toString(), path.toString(), "simulated transient failure");
            }
            super.moveAtomically(tempPath, path);
        }

        @Override
        protected void pauseBeforeRetry() {
        }
    }
}
