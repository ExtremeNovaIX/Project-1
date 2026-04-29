# Benchmark Harness

This directory now hosts a `HaluMem`-focused benchmark path.
It is designed to exercise the real memory extraction and write pipeline instead of the old raw transcript retrieval-only shortcut.

The normal chat flow is unchanged.
Extra HTTP endpoints are only exposed when the Spring profile `benchmark` is enabled.

## What It Measures

The current benchmark path measures three layers:

- memory extraction quality from raw dialogue
- memory state fidelity after sequential session updates
- grounded question answering using retrieved memory context

That means the benchmark now touches:

- `MemoryCompressionPipeline`
- `FactExtractionService`
- `MemoryWriteService`
- `MemorySearchTools`
- a benchmark-only QA layer
- a benchmark-only judge layer

It does not depend on `RpAgent`, `TaskSupervisor`, or checker flow control.

## Benchmark Endpoints

When the app starts with profile `benchmark`, these endpoints are enabled:

- `GET /api/benchmark/halumem/health`
- `POST /api/benchmark/halumem/reset`
- `POST /api/benchmark/halumem/ingest-session`
- `POST /api/benchmark/halumem/answer`
- `POST /api/benchmark/halumem/judge/memory`
- `POST /api/benchmark/halumem/judge/qa`

## Start And Stop

Start the benchmark server:

```text
benchmark\Start-BenchmarkServer.cmd
```

Or from PowerShell:

```powershell
.\benchmark\Start-BenchmarkServer.ps1
```

Stop it explicitly:

```text
benchmark\Stop-BenchmarkServer.cmd
```

## One-Click HaluMem Run

Place one of these files in one of the supported locations:

- `benchmark\halumem\HaluMem-Medium.jsonl`
- `benchmark\halumem\HaluMem-Easy.jsonl`
- `benchmark\halumem\HaluMem-Hard.jsonl`
- `benchmark\HaluMem-Medium.jsonl`
- `benchmark\data\halumem\HaluMem-Medium.jsonl`

Then run:

```text
benchmark\Run-HaluMem-OneClick.cmd
```

The script will:

- start the benchmark Spring profile if needed
- wait for `/api/benchmark/halumem/health`
- prefer `HaluMem-Medium.jsonl` when multiple files exist
- write outputs under:
  - `benchmark\out\halumem\`

## Direct Invocation

If you want to pass the dataset path explicitly:

```powershell
.\benchmark\halumem\Invoke-HaluMemBenchmark.ps1 `
  -DatasetPath "C:\benchmarks\HaluMem-Medium.jsonl" `
  -OutputDir ".\benchmark\out\halumem"
```

## Output Files

The runner writes:

- `halumem_<dataset>_<runId>.jsonl`
  - one JSON object per benchmark user
- `halumem_<dataset>_<runId>.summary.json`
  - aggregate extraction/state/QA metrics
- `halumem_<dataset>_<runId>.progress.json`
  - progress metadata while the run is active

## Notes

- This path intentionally replaces the old `LongMemEval` retrieval-only harness.
- The benchmark still runs against the isolated `benchmark` profile storage and port.
- Judge quality depends on the configured chat model, because extraction/state/QA scoring is model-judged.
