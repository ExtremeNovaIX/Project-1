package p1.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import p1.infrastructure.vector.MemoryVectorLibrary;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = "^(?!default_value$).+")
class EmbeddingServiceTest {

    private static final String QUERY = "query";
    private static final int MAX_RESULTS = 3;
    private static final double MIN_SCORE = 0.6;
    private static final String SENTENCE_1 = "李响";
    private static final String SENTENCE_2 = "李想";

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void searchEmbedding_shouldUseRealEmbeddingModel() {
        EmbeddingSearchResult<TextSegment> result =
                embeddingService.searchEmbedding("default", MemoryVectorLibrary.ARCHIVE, QUERY, MAX_RESULTS, MIN_SCORE);

        assertNotNull(result);
        assertNotNull(result.matches());

        List<EmbeddingMatch<TextSegment>> matches = result.matches();
        System.out.println("query = " + QUERY);
        System.out.println("match count = " + matches.size());
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            String text = match.embedded() == null ? "<null>" : match.embedded().text();
            System.out.println("match[" + i + "] score = " + match.score());
            System.out.println("match[" + i + "] text = " + text);
        }
    }

    @Test
    void compareTwoSentences_shouldPrintCosineSimilarity() {
        Embedding embedding1 = embeddingModel.embed(SENTENCE_1).content();
        Embedding embedding2 = embeddingModel.embed(SENTENCE_2).content();

        assertNotNull(embedding1);
        assertNotNull(embedding2);

        double similarity = cosineSimilarity(embedding1.vector(), embedding2.vector());

        System.out.println("sentence1 = " + SENTENCE_1);
        System.out.println("sentence2 = " + SENTENCE_2);
        System.out.println("cosine similarity = " + similarity);
    }

    private static double cosineSimilarity(float[] vector1, float[] vector2) {
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Embedding dimensions do not match");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
