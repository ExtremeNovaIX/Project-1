package p1.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import p1.component.ai.vector.MemoryVectorDocument;
import p1.component.ai.vector.MemoryVectorDocumentIds;
import p1.component.ai.vector.MemoryVectorStore;
import p1.model.MemoryArchiveEntity;
import p1.repo.MemoryArchiveRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final MemoryVectorStore memoryVectorStore;
    private final EmbeddingModel embeddingModel;
    private final MemoryArchiveRepository archiveRepo;

    public EmbeddingSearchResult<TextSegment> searchEmbedding(String query, int maxResults, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        return memoryVectorStore.search(queryEmbedding, maxResults, minScore);
    }

    public List<MemoryArchiveMatch> searchMemoryArchives(String query, int maxResults, double minScore) {
        return searchEmbedding(query, maxResults, minScore).matches().stream()
                .map(this::toArchiveMatch)
                .filter(match -> match != null)
                .toList();
    }

    public void indexNewMemoryArchive(MemoryArchiveEntity archive) {
        MemoryVectorDocument document = buildArchiveDocument(archive);
        if (document == null) {
            log.warn("[向量索引写入跳过] 记忆 ID {} 无法构建有效索引文本。", archive == null ? null : archive.getId());
            return;
        }

        try {
            memoryVectorStore.add(document);
            log.info("[向量索引写入] 已为记忆 ID {} 写入新向量，当前后端：{}。", archive.getId(), memoryVectorStore.backendType());
        } catch (RuntimeException e) {
            if (!shouldFallbackToRebuild(e)) {
                throw e;
            }

            log.warn("[向量索引写入降级] 记忆 ID {} 的增量写入失败，检测到索引 schema 冲突，转为全量重建。", archive.getId(), e);
            rebuildAllMemoryArchiveEmbeddings();
        }
    }

    public void refreshMemoryArchiveEmbedding(MemoryArchiveEntity archive) {
        MemoryVectorDocument document = buildArchiveDocument(archive);
        if (document == null) {
            log.warn("[向量索引更新跳过] 记忆 ID {} 无法构建有效索引文本。", archive == null ? null : archive.getId());
            return;
        }

        if (memoryVectorStore.supportsDocumentUpdate()) {
            try {
                memoryVectorStore.update(document);
                log.info("[向量索引更新] 已增量更新记忆 ID {} 的向量，当前后端：{}。", archive.getId(), memoryVectorStore.backendType());
                return;
            } catch (RuntimeException e) {
                if (!shouldFallbackToRebuild(e)) {
                    throw e;
                }

                log.warn("[向量索引更新降级] 记忆 ID {} 的增量更新失败，检测到索引 schema 冲突，转为全量重建。", archive.getId(), e);
                rebuildAllMemoryArchiveEmbeddings();
                return;
            }
        }

        log.info("[向量索引更新] 当前后端 {} 不支持单条更新，转为全量重建。", memoryVectorStore.backendType());
        rebuildAllMemoryArchiveEmbeddings();
    }

    public void rebuildAllMemoryArchiveEmbeddings() {
        List<MemoryArchiveEntity> archives = archiveRepo.findAll(Sort.by(Sort.Direction.ASC, "id"));
        List<MemoryVectorDocument> documents = archives.stream()
                .map(this::buildArchiveDocument)
                .filter(document -> document != null)
                .toList();

        memoryVectorStore.rebuild(documents);
    }

    private MemoryVectorDocument buildArchiveDocument(MemoryArchiveEntity archive) {
        if (archive == null || archive.getId() == null) {
            return null;
        }

        String indexText = buildArchiveIndexText(archive);
        if (indexText.isBlank()) {
            return null;
        }

        Metadata metadata = new Metadata();
        metadata.put("db_id", String.valueOf(archive.getId()));
        metadata.put("vector_doc_id", buildDocumentId(archive.getId()));

        TextSegment segment = TextSegment.from(indexText, metadata);
        Response<Embedding> embeddingResponse = embeddingModel.embed(segment);
        return new MemoryVectorDocument(buildDocumentId(archive.getId()), embeddingResponse.content(), segment);
    }

    private MemoryArchiveMatch toArchiveMatch(EmbeddingMatch<TextSegment> match) {
        if (match == null || match.embedded() == null || match.embedded().metadata() == null) {
            return null;
        }

        String dbIdText = match.embedded().metadata().getString("db_id");
        if (dbIdText == null || dbIdText.isBlank()) {
            log.warn("[向量检索结果跳过] 检索命中缺少 db_id 元数据。");
            return null;
        }

        Long archiveId;
        try {
            archiveId = Long.parseLong(dbIdText);
        } catch (NumberFormatException e) {
            log.warn("[向量检索结果跳过] 检索命中的 db_id {} 不是有效数字。", dbIdText);
            return null;
        }

        MemoryArchiveEntity archive = archiveRepo.findById(archiveId).orElse(null);
        if (archive == null) {
            log.warn("[向量检索结果跳过] db_id {} 对应的 MemoryArchive 不存在。", archiveId);
            return null;
        }
        return new MemoryArchiveMatch(archive, match.score());
    }

    private String buildArchiveIndexText(MemoryArchiveEntity archive) {
        String keywordSummary = normalize(archive.getKeywordSummary());
        if (!keywordSummary.isBlank()) {
            return keywordSummary;
        }

        String detailedSummary = normalize(archive.getDetailedSummary());
        if (!detailedSummary.isBlank()) {
            log.warn("[向量索引文本降级] 记忆 ID {} 缺少关键词摘要，降级使用详细摘要写入向量库。", archive.getId());
        }
        return detailedSummary;
    }

    private String buildDocumentId(Long archiveId) {
        return MemoryVectorDocumentIds.archiveDocumentId(archiveId);
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private boolean shouldFallbackToRebuild(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && message.contains("cannot change field \"embedding\"")
                    && message.contains("vector similarity function")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public record MemoryArchiveMatch(MemoryArchiveEntity archive, double score) {
    }
}
