package p1.infrastructure.vector;

/**
 * 向量库类型。
 * 当前每个 session 下固定预留三种库：
 * 1. archive：长期事件检索库。
 * 2. recent-24h：24 小时窗口库。
 * 3. tag：标签能力预留库。
 */
public enum MemoryVectorLibrary {

    ARCHIVE("archive"),
    RECENT_24H("recent-24h"),
    TAG("tag");

    private final String directoryName;

    MemoryVectorLibrary(String directoryName) {
        this.directoryName = directoryName;
    }

    public String directoryName() {
        return directoryName;
    }
}
