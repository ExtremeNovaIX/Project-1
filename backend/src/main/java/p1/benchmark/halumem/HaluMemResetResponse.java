package p1.benchmark.halumem;

public record HaluMemResetResponse(int requestedSessionCount,
                                   int clearedSessionCount,
                                   int deletedFileCount) {
}
