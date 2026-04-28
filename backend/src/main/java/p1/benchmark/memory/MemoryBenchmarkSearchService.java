package p1.benchmark.memory;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import p1.component.agent.tools.MemorySearchTools;
import p1.model.document.MemoryArchiveDocument;
import p1.service.markdown.MemoryArchiveStore;
import p1.utils.SessionUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemoryBenchmarkSearchService {

    private final MemorySearchTools memorySearchTools;
    private final MemoryArchiveStore archiveStore;

    public BenchmarkSearchResponse search(BenchmarkSearchRequest request) {
        String sessionId = SessionUtil.normalizeSessionId(request.sessionId());
        MemorySearchTools.MemorySearchResult result;
        MDC.put("sessionId", sessionId);
        try {
            result = memorySearchTools.searchLongTermMemory(request.query());
        } finally {
            MDC.remove("sessionId");
        }

        List<BenchmarkSearchResponse.BenchmarkSearchDocument> documents = new ArrayList<>();
        Map<String, SourceSessionAggregate> sourceSessions = new LinkedHashMap<>();
        int documentIndex = 1;
        for (MemorySearchTools.MemorySearchBundle bundle : safeBundles(result)) {
            BenchmarkSearchResponse.BenchmarkSearchDocument document = toDocument(documentIndex++, bundle);
            documents.add(document);

            for (String sourceSessionId : document.sourceSessionIds()) {
                sourceSessions.computeIfAbsent(sourceSessionId, ignored -> new SourceSessionAggregate(sourceSessionId))
                        .absorb(document);
            }
        }

        return new BenchmarkSearchResponse(
                sessionId,
                request.query(),
                trimToEmpty(result.status()),
                trimToEmpty(result.message()),
                result.truncated(),
                List.copyOf(documents),
                sourceSessions.values().stream()
                        .map(SourceSessionAggregate::toHit)
                        .toList()
        );
    }

    private List<MemorySearchTools.MemorySearchBundle> safeBundles(MemorySearchTools.MemorySearchResult result) {
        if (result == null || result.bundles() == null) {
            return List.of();
        }
        return result.bundles();
    }

    private BenchmarkSearchResponse.BenchmarkSearchDocument toDocument(int index,
                                                                       MemorySearchTools.MemorySearchBundle bundle) {
        List<Long> groupContextArchiveIds = safeGroupContext(bundle).stream()
                .map(MemorySearchTools.ArchiveNodeView::archiveId)
                .toList();
        List<Long> graphExpansionArchiveIds = safeGraphExpansion(bundle).stream()
                .map(MemorySearchTools.GraphExpansionItem::archiveId)
                .toList();
        List<String> sourceRefs = collectSourceRefs(groupContextArchiveIds, graphExpansionArchiveIds);
        List<String> sourceSessionIds = sourceRefs.stream()
                .filter(ref -> ref.startsWith("session:"))
                .map(ref -> ref.substring("session:".length()))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        return new BenchmarkSearchResponse.BenchmarkSearchDocument(
                "bundle-" + index,
                bundle.seedArchiveId(),
                trimToEmpty(bundle.seedGroupId()),
                bundle.seedScore(),
                sourceRefs,
                sourceSessionIds,
                groupContextArchiveIds,
                graphExpansionArchiveIds,
                renderDocumentText(bundle, sourceRefs)
        );
    }

    private List<MemorySearchTools.ArchiveNodeView> safeGroupContext(MemorySearchTools.MemorySearchBundle bundle) {
        if (bundle == null || bundle.groupContext() == null) {
            return List.of();
        }
        return bundle.groupContext();
    }

    private List<MemorySearchTools.GraphExpansionItem> safeGraphExpansion(MemorySearchTools.MemorySearchBundle bundle) {
        if (bundle == null || bundle.graphExpansion() == null) {
            return List.of();
        }
        return bundle.graphExpansion();
    }

    private List<String> collectSourceRefs(List<Long> groupContextArchiveIds, List<Long> graphExpansionArchiveIds) {
        Set<String> refs = new LinkedHashSet<>();
        for (Long archiveId : groupContextArchiveIds) {
            collectArchiveSourceRefs(archiveId, refs);
        }
        for (Long archiveId : graphExpansionArchiveIds) {
            collectArchiveSourceRefs(archiveId, refs);
        }
        return List.copyOf(refs);
    }

    private void collectArchiveSourceRefs(Long archiveId, Set<String> sink) {
        if (archiveId == null) {
            return;
        }
        Optional<MemoryArchiveDocument> archive = archiveStore.findById(archiveId);
        archive.map(MemoryArchiveDocument::getSourceRefs)
                .orElse(List.of())
                .stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(sink::add);
    }

    private String renderDocumentText(MemorySearchTools.MemorySearchBundle bundle, List<String> sourceRefs) {
        StringBuilder builder = new StringBuilder();
        builder.append("Seed archive: ")
                .append(bundle.seedArchiveId())
                .append(" | group=")
                .append(trimToEmpty(bundle.seedGroupId()))
                .append(" | score=")
                .append(String.format(Locale.ROOT, "%.4f", bundle.seedScore()));

        if (!sourceRefs.isEmpty()) {
            builder.append("\nSource refs: ").append(String.join(", ", sourceRefs));
        }

        List<MemorySearchTools.ArchiveNodeView> groupContext = safeGroupContext(bundle);
        if (!groupContext.isEmpty()) {
            builder.append("\nGroup context:");
            for (MemorySearchTools.ArchiveNodeView nodeView : groupContext) {
                builder.append("\n- [")
                        .append(nodeView.archiveId())
                        .append("] ")
                        .append(trimToEmpty(nodeView.topic()))
                        .append(" | ")
                        .append(firstNonBlank(nodeView.keywordSummary(), nodeView.narrative()));
            }
        }

        List<MemorySearchTools.GraphExpansionItem> graphExpansion = safeGraphExpansion(bundle);
        if (!graphExpansion.isEmpty()) {
            builder.append("\nGraph expansion:");
            for (MemorySearchTools.GraphExpansionItem item : graphExpansion) {
                builder.append("\n- [")
                        .append(item.archiveId())
                        .append("] ")
                        .append(trimToEmpty(item.priorityBucket()))
                        .append(" | ")
                        .append(trimToEmpty(item.topic()))
                        .append(" | path=")
                        .append(item.pathArchiveIds())
                        .append(" | relations=")
                        .append(item.pathRelations());
            }
        }

        return builder.toString().trim();
    }

    private String firstNonBlank(String left, String right) {
        String normalizedLeft = trimToEmpty(left);
        if (!normalizedLeft.isBlank()) {
            return normalizedLeft;
        }
        return trimToEmpty(right);
    }

    private String trimToEmpty(String text) {
        return text == null ? "" : text.trim();
    }

    private static final class SourceSessionAggregate {
        private final String sourceSessionId;
        private double scoreHint;
        private final Set<String> sourceRefs = new LinkedHashSet<>();
        private final List<String> documentIds = new ArrayList<>();

        private SourceSessionAggregate(String sourceSessionId) {
            this.sourceSessionId = sourceSessionId;
        }

        private void absorb(BenchmarkSearchResponse.BenchmarkSearchDocument document) {
            scoreHint = Math.max(scoreHint, document.seedScore());
            sourceRefs.addAll(document.sourceRefs());
            if (!documentIds.contains(document.documentId())) {
                documentIds.add(document.documentId());
            }
        }

        private BenchmarkSearchResponse.BenchmarkSourceSessionHit toHit() {
            return new BenchmarkSearchResponse.BenchmarkSourceSessionHit(
                    sourceSessionId,
                    scoreHint,
                    List.copyOf(sourceRefs),
                    List.copyOf(documentIds)
            );
        }
    }
}
