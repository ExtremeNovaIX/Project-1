package p1.infrastructure.vector;

/**
 * archive 相关的向量库子集。
 * 这个枚举只覆盖“以 archive 事件为源”的那几类库，避免业务层直接拿更大的 MemoryVectorLibrary 到处乱传。
 */
public enum ArchiveVectorLibrary {

    ARCHIVE(MemoryVectorLibrary.ARCHIVE),
    RECENT_24H(MemoryVectorLibrary.RECENT_24H);

    private final MemoryVectorLibrary rootLibrary;

    ArchiveVectorLibrary(MemoryVectorLibrary rootLibrary) {
        this.rootLibrary = rootLibrary;
    }

    public MemoryVectorLibrary rootLibrary() {
        return rootLibrary;
    }
}
