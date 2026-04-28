package p1.benchmark.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@CrossOrigin
@Profile("benchmark")
@RequestMapping("/api/benchmark/memory")
@RequiredArgsConstructor
public class MemoryBenchmarkController {

    private final MemoryBenchmarkIngestionService ingestionService;
    private final MemoryBenchmarkSearchService searchService;
    private final MemoryBenchmarkSessionCleaner sessionCleaner;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/reset")
    public BenchmarkResetResponse reset(@RequestBody BenchmarkResetRequest request) {
        return sessionCleaner.reset(request);
    }

    @PostMapping("/ingest")
    public BenchmarkIngestResponse ingest(@RequestBody BenchmarkIngestRequest request) {
        return ingestionService.ingest(request);
    }

    @PostMapping("/search")
    public BenchmarkSearchResponse search(@RequestBody BenchmarkSearchRequest request) {
        return searchService.search(request);
    }
}
