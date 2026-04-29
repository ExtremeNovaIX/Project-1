package p1.benchmark.halumem;

import java.util.List;

public record HaluMemRetrievedDocument(String documentId,
                                       Long seedArchiveId,
                                       String seedGroupId,
                                       double seedScore,
                                       List<String> sourceRefs,
                                       List<String> sourceSessionIds,
                                       List<Long> groupContextArchiveIds,
                                       List<Long> graphExpansionArchiveIds,
                                       String text) {
}
