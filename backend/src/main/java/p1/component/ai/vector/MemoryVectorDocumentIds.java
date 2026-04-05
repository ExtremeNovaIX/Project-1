package p1.component.ai.vector;

public final class MemoryVectorDocumentIds {

    private MemoryVectorDocumentIds() {
    }

    public static String archiveDocumentId(Long archiveId) {
        return "memory-archive-" + archiveId;
    }
}
