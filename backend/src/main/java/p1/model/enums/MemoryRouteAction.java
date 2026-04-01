package p1.model.enums;

public enum MemoryRouteAction {
    DISCARD,       // 相似度极高 (对现有记忆的无意义重复)，直接丢弃
    NEEDS_JUDGE,   // 相似度中等 (疑似打补丁/状态更新)，需要 LLM 裁判复核
    INSERT_NEW     // 相似度低 (全新事件)，直接存为新记忆
}