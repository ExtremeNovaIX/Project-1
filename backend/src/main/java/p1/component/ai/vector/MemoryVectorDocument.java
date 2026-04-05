package p1.component.ai.vector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

public record MemoryVectorDocument(String documentId, Embedding embedding, TextSegment segment) {
}
