package p1.service.test;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import p1.service.ChatService;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Tag("manual")
class StoryReplayManualTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private StoryMarkdownChunker storyMarkdownChunker;

    @Test
    void shouldReplayFixedStoryFileLocally() {
        TestChatService service = new TestChatService(
                chatService,
                storyMarkdownChunker
        );
        StoryReplayResult result = service.replayStoryFromFile("story-replay", 300000);

        assertTrue(result.chunkCount() > 0);
        System.out.printf(
                "story-replay finished | source=%s | chunks=%d | effectiveLength=%d | preview=%s%n",
                result.sourcePath(),
                result.chunkCount(),
                result.effectiveSourceLength(),
                result.previewChunks()
        );
    }
}