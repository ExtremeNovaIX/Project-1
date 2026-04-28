import argparse
import json
import sys
import time
from pathlib import Path
from typing import Any
from urllib import error, request


def log(message: str) -> None:
    print(message, flush=True)


def api_call(server_url: str, method: str, path: str, body: Any | None = None) -> Any:
    url = server_url.rstrip("/") + path
    payload = None
    headers: dict[str, str] = {}
    if body is not None:
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"

    req = request.Request(url, data=payload, method=method, headers=headers)
    try:
        with request.urlopen(req, timeout=300) as response:
            text = response.read().decode("utf-8")
            return json.loads(text) if text else None
    except error.HTTPError as exc:
        body_text = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Benchmark API call failed: {method} {path}\n{body_text}") from exc
    except error.URLError as exc:
        raise RuntimeError(f"Benchmark API call failed: {method} {path}\n{exc}") from exc


def get_items(dataset: Any) -> list[Any]:
    if isinstance(dataset, list):
        return dataset
    if isinstance(dataset, dict):
        if dataset.get("question") or dataset.get("question_id") or dataset.get("questionId"):
            return [dataset]
        for name in ("data", "items", "questions"):
            value = dataset.get(name)
            if isinstance(value, list):
                return value
    raise RuntimeError("Unsupported dataset root shape.")


def first_string(node: Any, *names: str) -> str:
    if not isinstance(node, dict):
        return ""
    for name in names:
        value = node.get(name)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def first_array(node: Any, *names: str) -> list[Any]:
    if not isinstance(node, dict):
        return []
    for name in names:
        value = node.get(name)
        if isinstance(value, list):
            return value
        if value is not None and not isinstance(value, str):
            try:
                return list(value)
            except TypeError:
                continue
    return []


def resolve_sample_id(item: Any, index: int) -> str:
    resolved = first_string(item, "question_id", "questionId", "id")
    return resolved or f"sample-{index}"


def resolve_question_text(item: Any) -> str:
    resolved = first_string(item, "question", "query")
    if not resolved:
        raise RuntimeError("Missing question text.")
    return resolved


def resolve_gold_session_ids(item: Any) -> list[str]:
    values = first_array(item, "answer_session_ids", "answerSessionIds")
    return list(dict.fromkeys(value.strip() for value in values if isinstance(value, str) and value.strip()))


def resolve_source_sessions(item: Any) -> list[Any]:
    return first_array(item, "haystack_sessions", "haystackSessions", "sessions")


def resolve_source_session_ids(item: Any) -> list[str]:
    values = first_array(item, "haystack_session_ids", "haystackSessionIds", "session_ids", "sessionIds")
    return [value.strip() for value in values if isinstance(value, str) and value.strip()]


def resolve_session_id(session: Any, index: int) -> str:
    resolved = first_string(session, "session_id", "sessionId", "id")
    return resolved or f"source-session-{index}"


def resolve_messages(session: Any) -> list[Any]:
    if isinstance(session, list):
        return session
    if isinstance(session, dict):
        for name in ("messages", "conversation", "dialogue", "turns"):
            value = session.get(name)
            if isinstance(value, list):
                return value
    return []


def resolve_message_role(message: Any) -> str:
    role = first_string(message, "role", "speaker", "author").lower()
    if role in {"user", "human"}:
        return "user"
    if role in {"assistant", "ai"}:
        return "assistant"
    return ""


def resolve_message_text(message: Any) -> str:
    return first_string(message, "content", "text", "utterance", "message", "value")


def build_ingest_request(backend_session_id: str,
                         source_sessions: list[Any],
                         source_session_ids: list[str],
                         batch_message_count: int) -> dict[str, Any]:
    transcripts: list[dict[str, Any]] = []
    for session_index, session in enumerate(source_sessions):
        source_session_id = source_session_ids[session_index] if session_index < len(source_session_ids) else ""
        if not source_session_id:
            source_session_id = resolve_session_id(session, session_index)

        messages: list[dict[str, str]] = []
        for message in resolve_messages(session):
            role = resolve_message_role(message)
            text = resolve_message_text(message)
            if role and text:
                messages.append({"role": role, "content": text})

        if not messages:
            continue

        transcripts.append({
            "transcriptId": source_session_id,
            "sourceSessionId": source_session_id,
            "sourceRefs": ["dataset:longmemeval"],
            "messages": messages
        })

    if not transcripts:
        raise RuntimeError(f"No benchmark transcripts could be built for backend session [{backend_session_id}].")

    payload: dict[str, Any] = {
        "sessionId": backend_session_id,
        "transcripts": transcripts
    }
    if batch_message_count > 0:
        payload["batchMessageCount"] = batch_message_count
    return payload


def compute_recall(gold: list[str], predicted: list[str], k: int) -> float:
    if not gold:
        return 0.0
    top = predicted[:k]
    hits = sum(1 for gold_id in gold if gold_id in top)
    return hits / len(gold)


def compute_hit(gold: list[str], predicted: list[str], k: int) -> int:
    top = predicted[:k]
    return 1 if any(gold_id in top for gold_id in gold) else 0


def summarize(results: list[dict[str, Any]], run_id: str) -> dict[str, Any]:
    def avg(field: str) -> float:
        if not results:
            return 0.0
        return sum(float(result[field]) for result in results) / len(results)

    return {
        "runId": run_id,
        "sampleCount": len(results),
        "avgHitAt1": avg("hitAt1"),
        "avgHitAt3": avg("hitAt3"),
        "avgHitAt5": avg("hitAt5"),
        "avgRecallAt1": avg("recallAt1"),
        "avgRecallAt3": avg("recallAt3"),
        "avgRecallAt5": avg("recallAt5")
    }


def write_progress(progress_path: Path,
                   run_id: str,
                   dataset_path: Path,
                   total_samples: int,
                   processed_samples: int,
                   last_sample_id: str,
                   started_at: float) -> None:
    payload = {
        "runId": run_id,
        "datasetPath": str(dataset_path),
        "totalSamples": total_samples,
        "processedSamples": processed_samples,
        "lastSampleId": last_sample_id,
        "elapsedSeconds": round(time.time() - started_at, 2),
        "updatedAtEpochSeconds": time.time()
    }
    progress_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset-path", required=True)
    parser.add_argument("--output-path", required=True)
    parser.add_argument("--server-url", default="http://127.0.0.1:18080")
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--batch-message-count", type=int, default=0)
    args = parser.parse_args()

    dataset_path = Path(args.dataset_path)
    output_path = Path(args.output_path)
    progress_path = output_path.with_suffix(output_path.suffix + ".progress.json")

    api_call(args.server_url, "GET", "/api/benchmark/memory/health")

    dataset_size_mb = dataset_path.stat().st_size / (1024 * 1024)
    log(f"Loading dataset from {dataset_path} ({dataset_size_mb:.1f} MB)...")
    started_at = time.time()
    with dataset_path.open("r", encoding="utf-8-sig") as handle:
        dataset = json.load(handle)
    items = get_items(dataset)
    log(f"Parsed {len(items)} samples for run [{args.run_id}].")

    session_ids = [f"bench.longmemeval.{args.run_id}.{resolve_sample_id(item, index)}"
                   for index, item in enumerate(items)]
    log(f"Resetting {len(session_ids)} benchmark sessions...")
    api_call(args.server_url, "POST", "/api/benchmark/memory/reset", {"sessionIds": session_ids})

    results: list[dict[str, Any]] = []
    total = len(items)
    progress_interval = 5

    for index, item in enumerate(items, start=1):
        sample_id = resolve_sample_id(item, index - 1)
        backend_session_id = f"bench.longmemeval.{args.run_id}.{sample_id}"
        question = resolve_question_text(item)
        gold_session_ids = resolve_gold_session_ids(item)
        source_sessions = resolve_source_sessions(item)
        source_session_ids = resolve_source_session_ids(item)

        ingest_request = build_ingest_request(
            backend_session_id,
            source_sessions,
            source_session_ids,
            args.batch_message_count
        )
        ingest_response = api_call(args.server_url, "POST", "/api/benchmark/memory/ingest", ingest_request)
        search_response = api_call(args.server_url, "POST", "/api/benchmark/memory/search", {
            "sessionId": backend_session_id,
            "query": question
        })

        predicted_session_ids: list[str] = []
        for hit in search_response.get("rankedSourceSessions", []):
            source_session_id = hit.get("sourceSessionId")
            if isinstance(source_session_id, str) and source_session_id.strip():
                normalized = source_session_id.strip()
                if normalized not in predicted_session_ids:
                    predicted_session_ids.append(normalized)

        result = {
            "sampleId": sample_id,
            "backendSessionId": backend_session_id,
            "question": question,
            "goldSessionIds": gold_session_ids,
            "predictedSessionIds": predicted_session_ids,
            "hitAt1": compute_hit(gold_session_ids, predicted_session_ids, 1),
            "hitAt3": compute_hit(gold_session_ids, predicted_session_ids, 3),
            "hitAt5": compute_hit(gold_session_ids, predicted_session_ids, 5),
            "recallAt1": compute_recall(gold_session_ids, predicted_session_ids, 1),
            "recallAt3": compute_recall(gold_session_ids, predicted_session_ids, 3),
            "recallAt5": compute_recall(gold_session_ids, predicted_session_ids, 5),
            "ingest": ingest_response,
            "search": search_response
        }
        results.append(result)

        if index == 1 or index % progress_interval == 0 or index == total:
            elapsed = time.time() - started_at
            rate = index / elapsed if elapsed > 0 else 0.0
            eta_seconds = (total - index) / rate if rate > 0 else 0.0
            log(
                f"[{args.run_id}] processed {index}/{total} "
                f"({index / total:.1%}) | last={sample_id} | elapsed={elapsed / 60:.1f}m | "
                f"eta={eta_seconds / 60:.1f}m"
            )
            write_progress(progress_path, args.run_id, dataset_path, total, index, sample_id, started_at)

    summary = summarize(results, args.run_id)
    payload = {
        "summary": summary,
        "samples": results
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    if progress_path.exists():
        progress_path.unlink()

    log(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(str(exc), file=sys.stderr, flush=True)
        raise
