# Benchmark Harness

This directory adds a standalone benchmark path for the memory system.
It does not replace the normal chat flow and it only exposes extra HTTP endpoints when the Spring profile `benchmark` is enabled.

## What It Benchmarks

The current harness targets retrieval-quality comparison across memory systems.
It is intentionally narrower than the full RP chain.

Current scope:

- ingest benchmark transcripts into the existing fact-extraction -> archive -> graph -> vector pipeline
- preserve benchmark provenance through `sourceRefs`
- search through the existing `MemorySearchTools`
- export ranked source-session hits for retrieval-style benchmarks such as `LongMemEval`

This first version focuses on retrieval metrics against gold source sessions.
That makes it stable and directly comparable.

## Benchmark Endpoints

When the app starts with profile `benchmark`, these endpoints are enabled:

- `GET /api/benchmark/memory/health`
- `POST /api/benchmark/memory/reset`
- `POST /api/benchmark/memory/ingest`
- `POST /api/benchmark/memory/search`

The normal `/api/chat/send` flow is unchanged.

## Start Server

From the repo root:

```powershell
.\benchmark\Start-BenchmarkServer.ps1
```

Or manually:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=benchmark"
```

If you prefer double-click startup on Windows, use:

```text
benchmark\Start-BenchmarkServer.cmd
```

To stop the benchmark server explicitly:

```text
benchmark\Stop-BenchmarkServer.cmd
```

## LongMemEval Retrieval Run

The included script runs a retrieval-only pass against a LongMemEval-style JSON file and writes per-sample plus aggregate metrics.

```powershell
.\benchmark\longmemeval\Invoke-LongMemEvalRetrieval.ps1 `
  -DatasetPath "C:\benchmarks\longmemeval.json" `
  -OutputPath "C:\benchmarks\longmemeval-result.json"
```

Optional parameters:

- `-ServerUrl` defaults to `http://127.0.0.1:18080`
- `-RunId` defaults to a timestamp-based id
- `-BatchMessageCount` is optional

The PowerShell wrapper now delegates dataset parsing to Python so large benchmark files do not get stuck in `ConvertFrom-Json`.
The runner prints progress updates as it processes samples.

## One-Click Run

If your dataset files are already placed in one of these locations:

- `benchmark\data\longmemeval\`
- `benchmark\longmemeval\`
- `benchmark\`

you can run everything with:

```text
benchmark\Run-LongMemEval-OneClick.cmd
```

It will:

- start the benchmark Spring profile if needed
- wait for `/api/benchmark/memory/health`
- by default run:
  - `longmemeval_oracle.json`
  - `longmemeval_s_cleaned.json`
- write per-dataset outputs plus a merged summary under:
  - `benchmark\out\longmemeval\`

If you explicitly want the medium split too, run:

```powershell
.\benchmark\Run-LongMemEval-OneClick.ps1 -Datasets oracle,s_cleaned,m_cleaned
```

If you only want the practical comparison split, use:

```text
benchmark\Run-LongMemEval-S-OneClick.cmd
```

The script expects a LongMemEval-like shape and uses flexible field resolution for:

- `question_id` / `questionId` / `id`
- `question` / `query`
- `answer_session_ids` / `answerSessionIds`
- `haystack_sessions` / `haystackSessions` / `sessions`

Each source session is ingested into one backend benchmark session namespace, and the backend stores provenance as:

- `session:<sourceSessionId>`
- `transcript:<transcriptId>`

The output includes:

- `hit@1`, `hit@3`, `hit@5`
- `recall@1`, `recall@3`, `recall@5`
- per-sample retrieved source sessions
- raw backend search response snippets

## Comparison Strategy

For horizontal comparison, keep these fixed:

- same benchmark split
- same retrieval metric
- same ingest granularity
- same backend model and embedding model
- same batch size

If you later want answer-level comparison, layer a fixed answerer and judge on top of the search output instead of changing the memory benchmark path itself.
