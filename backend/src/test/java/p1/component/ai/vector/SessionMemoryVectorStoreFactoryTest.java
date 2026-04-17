package p1.component.ai.vector;

import org.junit.jupiter.api.Test;
import p1.config.prop.AssistantProperties;
import p1.infrastructure.vector.MemoryVectorLibrary;
import p1.infrastructure.vector.MemoryVectorStore;
import p1.infrastructure.vector.SessionMemoryVectorStoreFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SessionMemoryVectorStoreFactoryTest {

    @Test
    void shouldReuseSameStoreForSameSessionAndLibrary() {
        SessionMemoryVectorStoreFactory factory = new SessionMemoryVectorStoreFactory(properties("target/vector-test"));

        MemoryVectorStore first = factory.getStore("session-a", MemoryVectorLibrary.ARCHIVE);
        MemoryVectorStore second = factory.getStore("session-a", MemoryVectorLibrary.ARCHIVE);

        assertSame(first, second);
    }

    @Test
    void shouldCreateDifferentStoresForDifferentLibraries() {
        SessionMemoryVectorStoreFactory factory = new SessionMemoryVectorStoreFactory(properties("target/vector-test"));

        MemoryVectorStore archiveStore = factory.getStore("session-a", MemoryVectorLibrary.ARCHIVE);
        MemoryVectorStore tagStore = factory.getStore("session-a", MemoryVectorLibrary.TAG);

        assertNotSame(archiveStore, tagStore);
    }

    @Test
    void shouldResolveSessionScopedDirectory() {
        SessionMemoryVectorStoreFactory factory = new SessionMemoryVectorStoreFactory(properties("target/vector-test"));

        Path archivePath = factory.resolveStorePath("session-a", MemoryVectorLibrary.ARCHIVE);
        Path tagPath = factory.resolveStorePath("session-a", MemoryVectorLibrary.TAG);

        assertTrue(archivePath.toString().replace('\\', '/').endsWith("target/vector-test/sessions/session-a/archive"));
        assertTrue(tagPath.toString().replace('\\', '/').endsWith("target/vector-test/sessions/session-a/tag"));
    }

    private AssistantProperties properties(String basePath) {
        AssistantProperties properties = new AssistantProperties();
        AssistantProperties.EmbeddingStoreConfig embeddingStoreConfig = new AssistantProperties.EmbeddingStoreConfig();
        embeddingStoreConfig.setPath(basePath);
        properties.setEmbeddingStore(embeddingStoreConfig);
        return properties;
    }
}
