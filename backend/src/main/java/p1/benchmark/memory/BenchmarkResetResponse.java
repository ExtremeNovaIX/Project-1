package p1.benchmark.memory;

public record BenchmarkResetResponse(int requestedSessionCount,
                                     int clearedSessionCount,
                                     int deletedFileCount) {
}
