package p1.benchmark.halumem;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import p1.component.agent.tools.MemorySearchTools;
import p1.service.markdown.MemoryArchiveStore;
import p1.utils.SessionUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HaluMemAnsweringService {

    private final MemorySearchTools memorySearchTools;
    private final MemoryArchiveStore archiveStore;
    private final HaluMemQaAnswerAiService qaAnswerAiService;

    public HaluMemAnswerResponse answer(HaluMemAnswerRequest request) {
        String sessionId = SessionUtil.normalizeSessionId(request.sessionId());
        String question = request.question() == null ? "" : request.question().trim();

        MemorySearchTools.MemorySearchResult searchResult;
        MDC.put("sessionId", sessionId);
        try {
            searchResult = memorySearchTools.searchLongTermMemory(question);
        } finally {
            MDC.remove("sessionId");
        }

        List<HaluMemRetrievedDocument> documents = HaluMemRenderSupport.toRetrievedDocuments(searchResult, archiveStore);
        String retrievedContext = HaluMemRenderSupport.renderRetrievedContext(documents);
        HaluMemQaAnswerAiResult aiResult = qaAnswerAiService.answer(question, retrievedContext);

        Set<String> rankedSourceSessionIds = new LinkedHashSet<>();
        for (HaluMemRetrievedDocument document : documents) {
            rankedSourceSessionIds.addAll(document.sourceSessionIds());
        }

        return new HaluMemAnswerResponse(
                sessionId,
                question,
                trimToEmpty(searchResult.status()),
                trimToEmpty(searchResult.message()),
                trimToEmpty(aiResult.answer()),
                trimToEmpty(aiResult.thinking()),
                searchResult.truncated(),
                List.copyOf(documents),
                new ArrayList<>(rankedSourceSessionIds),
                retrievedContext
        );
    }

    private String trimToEmpty(String text) {
        return text == null ? "" : text.trim();
    }
}
