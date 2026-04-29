package p1.infrastructure.vector;

public final class MemoryVectorDocumentIds {

    private MemoryVectorDocumentIds() {
    }

    public static String archiveDocumentId(Long archiveId) {
        return "memory-archive-" + archiveId;
    }

    public static String recent24hDocumentId(String groupId, Long archiveId) {
        return "recent-24h-" + groupId + "-" + archiveId;
    }

    public static String tagDocumentId(String tagId) {
        return "memory-tag-" + tagId;
    }
}
