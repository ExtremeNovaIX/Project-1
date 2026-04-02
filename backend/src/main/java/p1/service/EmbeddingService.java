package p1.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class EmbeddingService {

    private final EmbeddingStore<TextSegment> vectorStore;
    private final EmbeddingModel embeddingModel;

    /**
     * 搜索向量库中与查询最相似的向量
     *
     * @param query      查询文本
     * @param maxResults 最大返回结果数
     * @param minScore   最小相似度阈值
     * @return 搜索结果
     */
    public EmbeddingSearchResult<TextSegment> searchEmbedding(String query, int maxResults, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();

        return vectorStore.search(searchRequest);
    }

    public void saveEmbedding(String text, Metadata metadata) {
        TextSegment segment = TextSegment.from(text, metadata);
        Response<Embedding> embeddingResponse = embeddingModel.embed(segment);
        vectorStore.add(embeddingResponse.content(), segment);
    }
}