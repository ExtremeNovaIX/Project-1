package p1.component.ai.vector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;

import java.util.List;

public interface MemoryVectorStore {

    String backendType();

    boolean supportsDocumentUpdate();

    EmbeddingSearchResult<TextSegment> search(Embedding queryEmbedding, int maxResults, double minScore);

    void add(MemoryVectorDocument document);

    void update(MemoryVectorDocument document);

    void delete(String documentId);

    void rebuild(List<MemoryVectorDocument> documents);
}
