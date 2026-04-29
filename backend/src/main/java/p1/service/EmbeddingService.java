package p1.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import p1.config.prop.AssistantProperties;
import p1.config.runtime.RuntimeModelSettings;
import p1.config.runtime.RuntimeModelSettingsRegistry;
import p1.infrastructure.vector.MemoryVectorDocument;
import p1.infrastructure.vector.MemoryVectorLibrary;
import p1.infrastructure.vector.MemoryVectorStore;
import p1.infrastructure.vector.SessionMemoryVectorStoreFactory;
import p1.utils.SessionUtil;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final SessionMemoryVectorStoreFactory vectorStoreFactory;
    private final EmbeddingModel embeddingModel;
    private final AssistantProperties assistantProperties;
    private final RuntimeModelSettingsRegistry runtimeModelSettingsRegistry;

    /**
     * 在指定 session、指定库中执行语义检索。
     */
    public EmbeddingSearchResult<TextSegment> searchEmbedding(String sessionId,
                                                              MemoryVectorLibrary library,
                                                              String query,
                                                              int maxResults,
                                                              double minScore) {
        String normalizedSessionId = SessionUtil.normalizeSessionId(sessionId);
        Embedding queryEmbedding = embeddingModelFor(normalizedSessionId).embed(query).content();
        return store(normalizedSessionId, library).search(queryEmbedding, maxResults, minScore);
    }

    public Embedding embed(String sessionId, TextSegment segment) {
        return embeddingModelFor(sessionId).embed(segment).content();
    }

    /**
     * 通用写入入口。
     */
    public void indexDocument(String sessionId, MemoryVectorLibrary library, MemoryVectorDocument document) {
        store(sessionId, library).add(document);
    }

    /**
     * 通用更新入口。
     */
    public void updateDocument(String sessionId, MemoryVectorLibrary library, MemoryVectorDocument document) {
        MemoryVectorStore store = store(sessionId, library);
        if (store.supportsDocumentUpdate()) {
            store.update(document);
            return;
        }
        store.delete(document.documentId());
        store.add(document);
    }

    /**
     * 通用删除入口。
     */
    public void deleteDocument(String sessionId, MemoryVectorLibrary library, String documentId) {
        store(sessionId, library).delete(documentId);
    }

    /**
     * 通用批量删除入口。
     */
    public void deleteDocuments(String sessionId, MemoryVectorLibrary library, List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        for (String documentId : documentIds) {
            deleteDocument(sessionId, library, documentId);
        }
    }

    /**
     * 通用全量重建入口。
     */
    public void rebuildLibrary(String sessionId, MemoryVectorLibrary library, List<MemoryVectorDocument> documents) {
        store(sessionId, library).rebuild(documents);
    }

    private MemoryVectorStore store(String sessionId, MemoryVectorLibrary library) {
        return vectorStoreFactory.getStore(SessionUtil.normalizeSessionId(sessionId), library);
    }

    private EmbeddingModel embeddingModelFor(String sessionId) {
        return runtimeModelSettingsRegistry.find(sessionId)
                .filter(this::hasEmbeddingOverride)
                .map(this::buildRuntimeEmbeddingModel)
                .orElse(embeddingModel);
    }

    private EmbeddingModel buildRuntimeEmbeddingModel(RuntimeModelSettings settings) {
        AssistantProperties.EmbeddingModelConfig defaults = assistantProperties.activeEmbeddingModel();
        String baseUrl = firstText(settings.embeddingBaseUrl(), defaults.getBaseUrl());
        String apiKey = firstText(settings.embeddingApiKey(), defaults.getApiKey());
        String modelName = firstText(settings.embeddingModelName(), defaults.getModelName());

        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName);

        if (StringUtils.hasText(apiKey)) {
            builder.apiKey(apiKey);
        }
        return builder.build();
    }

    private boolean hasEmbeddingOverride(RuntimeModelSettings settings) {
        return settings != null
                && (StringUtils.hasText(settings.embeddingBaseUrl())
                || StringUtils.hasText(settings.embeddingApiKey())
                || StringUtils.hasText(settings.embeddingModelName()));
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
