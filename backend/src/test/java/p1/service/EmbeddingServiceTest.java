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
    private static final String SENTENCE_1 = "用户更正了关于山羊“棉花糖”眼睛颜色的记忆。经翻看旧相册确认，其眼睛在阳光下实为普通的浅棕色，而非之前描述的“夕阳下的琥珀色”。用户反思自己可能对回忆进行了美化。";
    private static final String SENTENCE_2 = "用户小时候在乡下外婆家养了一只名叫“棉花糖”的白色山羊，它非常聪明，会自己用角顶开老旧的木栅栏门溜出去，最爱吃外婆家后院的苜蓿地，每次都会吃得肚子圆滚滚才回来。后来外婆搬到城里，棉花糖被送给了隔壁村的张大爷，在那里它成了“孩子王”，带着一群小羊满山跑。";

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
