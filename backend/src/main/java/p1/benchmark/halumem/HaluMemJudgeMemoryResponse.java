package p1.benchmark.halumem;

import java.util.List;

public record HaluMemJudgeMemoryResponse(int goldCount,
                                         int systemCount,
                                         int matchedGoldCount,
                                         int supportedSystemCount,
                                         double precision,
                                         double recall,
                                         double f1,
                                         List<String> missingGoldItems,
                                         List<String> unsupportedSystemItems,
                                         String reasoning) {
}
