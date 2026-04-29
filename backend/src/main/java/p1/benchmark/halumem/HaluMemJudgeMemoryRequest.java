package p1.benchmark.halumem;

import java.util.List;

public record HaluMemJudgeMemoryRequest(List<String> goldMemoryPoints,
                                        List<String> systemMemoryItems) {
}
