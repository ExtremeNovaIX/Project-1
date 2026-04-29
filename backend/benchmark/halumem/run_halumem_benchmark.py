import argparse
import json
import socket
import sys
import time
from pathlib import Path
from typing import Any
from urllib import error, request


def log(message: str) -> None:
    print(message, flush=True)


def api_call(
    server_url: str,
    method: str,
    path: str,
    body: Any | None = None,
    timeout_seconds: int = 900,
    max_attempts: int = 3,
) -> Any:
    url = server_url.rstrip("/") + path
    payload = None
    headers: dict[str, str] = {}
    if body is not None:
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"

    req = request.Request(url, data=payload, method=method, headers=headers)
    last_error: Exception | None = None
    for attempt in range(1, max_attempts + 1):
        try:
            with request.urlopen(req, timeout=timeout_seconds) as response:
                text = response.read().decode("utf-8")
                return json.loads(text) if text else None
        except error.HTTPError as exc:
            body_text = exc.read().decode("utf-8", errors="replace")
            last_error = RuntimeError(f"Benchmark API call failed: {method} {path}\n{body_text}")
            should_retry = exc.code >= 500 and attempt < max_attempts
            if not should_retry:
                raise last_error from exc
            sleep_seconds = min(30, 2 * attempt)
            log(f"Retrying {method} {path} after HTTP {exc.code}. attempt={attempt}/{max_attempts} sleep={sleep_seconds}s")
            time.sleep(sleep_seconds)
        except (error.URLError, TimeoutError, socket.timeout) as exc:
            last_error = RuntimeError(f"Benchmark API call failed: {method} {path}\n{exc}")
            if attempt >= max_attempts:
                raise last_error from exc
            sleep_seconds = min(30, 2 * attempt)
            log(f"Retrying {method} {path} after network failure. attempt={attempt}/{max_attempts} sleep={sleep_seconds}s")
            time.sleep(sleep_seconds)

    if last_error is not None:
        raise last_error
    raise RuntimeError(f"Benchmark API call failed unexpectedly: {method} {path}")


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
    return []


def load_samples(dataset_path: Path) -> list[dict[str, Any]]:
    samples: list[dict[str, Any]] = []
    if dataset_path.suffix.lower() == ".jsonl":
        for line_number, line in enumerate(dataset_path.read_text(encoding="utf-8").splitlines(), start=1):
            stripped = line.strip()
            if not stripped:
                continue
            try:
                item = json.loads(stripped)
            except json.JSONDecodeError as exc:
                raise RuntimeError(f"Invalid JSONL at line {line_number}: {exc}") from exc
            if not isinstance(item, dict):
                raise RuntimeError(f"Invalid sample at line {line_number}: expected an object.")
            samples.append(item)
        return samples

    payload = json.loads(dataset_path.read_text(encoding="utf-8"))
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    if isinstance(payload, dict):
        for key in ("data", "items", "users", "samples"):
            value = payload.get(key)
            if isinstance(value, list):
                return [item for item in value if isinstance(item, dict)]
    raise RuntimeError("Unsupported HaluMem dataset shape.")


def resolve_user_id(sample: dict[str, Any], index: int) -> str:
    return first_string(sample, "user_id", "userId", "id") or f"user-{index}"


def resolve_sessions(sample: dict[str, Any]) -> list[dict[str, Any]]:
    return [item for item in first_array(sample, "sessions") if isinstance(item, dict)]


def resolve_session_label(session: dict[str, Any], index: int) -> str:
    return first_string(session, "session_id", "sessionId", "id", "label") or f"session-{index}"


def resolve_memory_point_text(point: Any) -> str:
    if isinstance(point, str):
        return point.strip()
    if isinstance(point, dict):
        return (
            first_string(point, "memory_point", "memoryPoint", "content", "text", "fact", "summary")
            or json.dumps(point, ensure_ascii=False)
        )
    return ""


def resolve_memory_points(session: dict[str, Any]) -> list[str]:
    points = []
    for point in first_array(session, "memory_points", "memoryPoints"):
        text = resolve_memory_point_text(point)
        if text:
            points.append(text)
    return points


def resolve_message_role(message: Any) -> str:
    role = first_string(message, "role", "speaker", "author").lower()
    if role in {"user", "human"}:
        return "user"
    if role in {"assistant", "ai"}:
        return "assistant"
    return ""


def resolve_message_text(message: Any) -> str:
    return first_string(message, "content", "text", "utterance", "message", "value")


def resolve_dialogue(session: dict[str, Any]) -> list[dict[str, str]]:
    dialogue = []
    for message in first_array(session, "dialogue", "messages", "conversation", "turns"):
        role = resolve_message_role(message)
        text = resolve_message_text(message)
        if role and text:
            dialogue.append({"role": role, "content": text})
    return dialogue


def resolve_questions(session: dict[str, Any]) -> list[dict[str, str]]:
    questions = []
    for item in first_array(session, "questions"):
        if not isinstance(item, dict):
            continue
        question = first_string(item, "question", "query")
        if not question:
            continue
        questions.append(
            {
                "question": question,
                "ground_truth": first_string(item, "ground_truth", "groundTruth", "answer", "reference_answer"),
                "context": first_string(item, "context"),
            }
        )
    return questions


def render_memory_item_text(item: Any) -> str:
    if not isinstance(item, dict):
        return ""
    for field in ("keywordSummary", "narrative", "topic"):
        value = item.get(field)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def select_state_memory_items(items: list[Any], session_label: str) -> list[dict[str, Any]]:
    session_ref = f"halumem_session:{session_label}"
    selected: list[dict[str, Any]] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        source_refs = first_array(item, "sourceRefs")
        if session_ref in source_refs:
            selected.append(item)
    return selected


def slugify(value: str) -> str:
    normalized = []
    for ch in value:
        if ch.isalnum() or ch in {"-", "_"}:
            normalized.append(ch)
        else:
            normalized.append("-")
    slug = "".join(normalized).strip("-")
    return slug or "sample"


def load_completed_user_ids(artifact_path: Path) -> set[str]:
    if not artifact_path.exists():
        return set()
    completed = set()
    for line in artifact_path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        item = json.loads(stripped)
        if isinstance(item, dict):
            user_id = first_string(item, "user_id", "userId", "id")
            if user_id:
                completed.add(user_id)
    return completed


def append_jsonl(path: Path, payload: dict[str, Any]) -> None:
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(payload, ensure_ascii=False) + "\n")


def compute_summary(results: list[dict[str, Any]], run_id: str, dataset_path: Path) -> dict[str, Any]:
    extraction_scores: list[dict[str, float]] = []
    state_scores: list[dict[str, float]] = []
    qa_scores: list[float] = []
    qa_hallucinations = 0
    qa_verdict_counts: dict[str, int] = {}
    session_count = 0
    question_count = 0

    for user in results:
        for session in user.get("sessions", []):
            session_count += 1
            extraction = session.get("extraction_judgement", {})
            state = session.get("state_judgement", {})
            extraction_scores.append(
                {
                    "precision": float(extraction.get("precision", 0.0)),
                    "recall": float(extraction.get("recall", 0.0)),
                    "f1": float(extraction.get("f1", 0.0)),
                }
            )
            state_scores.append(
                {
                    "precision": float(state.get("precision", 0.0)),
                    "recall": float(state.get("recall", 0.0)),
                    "f1": float(state.get("f1", 0.0)),
                }
            )
            for question in session.get("questions", []):
                question_count += 1
                judgement = question.get("qa_judgement", {})
                qa_scores.append(float(judgement.get("score", 0.0)))
                verdict = str(judgement.get("verdict", "UNKNOWN"))
                qa_verdict_counts[verdict] = qa_verdict_counts.get(verdict, 0) + 1
                if bool(judgement.get("hallucinated", False)):
                    qa_hallucinations += 1

    def avg(values: list[float]) -> float:
        return round(sum(values) / len(values), 4) if values else 0.0

    return {
        "runId": run_id,
        "datasetPath": str(dataset_path),
        "userCount": len(results),
        "sessionCount": session_count,
        "questionCount": question_count,
        "extraction": {
            "avgPrecision": avg([item["precision"] for item in extraction_scores]),
            "avgRecall": avg([item["recall"] for item in extraction_scores]),
            "avgF1": avg([item["f1"] for item in extraction_scores]),
        },
        "state": {
            "avgPrecision": avg([item["precision"] for item in state_scores]),
            "avgRecall": avg([item["recall"] for item in state_scores]),
            "avgF1": avg([item["f1"] for item in state_scores]),
        },
        "qa": {
            "avgScore": avg(qa_scores),
            "hallucinationRate": round(qa_hallucinations / question_count, 4) if question_count else 0.0,
            "correctRate": round(qa_verdict_counts.get("CORRECT", 0) / question_count, 4) if question_count else 0.0,
            "verdictCounts": qa_verdict_counts,
        },
    }


def write_progress(progress_path: Path, run_id: str, dataset_path: Path, total_users: int, processed_users: int, last_user_id: str, started_at: float) -> None:
    payload = {
        "runId": run_id,
        "datasetPath": str(dataset_path),
        "totalUsers": total_users,
        "processedUsers": processed_users,
        "lastUserId": last_user_id,
        "elapsedSeconds": round(time.time() - started_at, 2),
        "updatedAtEpochSeconds": time.time(),
    }
    progress_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run HaluMem benchmark against the backend benchmark profile.")
    parser.add_argument("--dataset-path", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--server-url", default="http://127.0.0.1:18080")
    parser.add_argument("--run-id", default="")
    args = parser.parse_args()

    dataset_path = Path(args.dataset_path).resolve()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    run_id = args.run_id.strip() or time.strftime("%Y%m%d%H%M%S")
    dataset_stem = dataset_path.stem.replace(" ", "_").lower()
    output_base = output_dir / f"halumem_{dataset_stem}_{run_id}"
    artifact_path = output_base.with_suffix(".jsonl")
    progress_path = Path(str(output_base) + ".progress.json")
    summary_path = Path(str(output_base) + ".summary.json")

    samples = load_samples(dataset_path)
    completed_user_ids = load_completed_user_ids(artifact_path)
    started_at = time.time()
    total_users = len(samples)
    processed_users = len(completed_user_ids)

    log(f"Loaded {total_users} HaluMem users from {dataset_path}")
    if completed_user_ids:
        log(f"Resuming from existing artifact. Already completed {processed_users} users.")

    for index, sample in enumerate(samples, start=1):
        user_id = resolve_user_id(sample, index)
        if user_id in completed_user_ids:
            continue

        backend_session_id = f"bench.halumem.{run_id}.{slugify(user_id)}"
        sessions_output: list[dict[str, Any]] = []
        api_call(
            args.server_url,
            "POST",
            "/api/benchmark/halumem/reset",
            {"sessionIds": [backend_session_id]},
        )

        for session_index, session in enumerate(resolve_sessions(sample), start=1):
            session_label = resolve_session_label(session, session_index)
            memory_points = resolve_memory_points(session)
            dialogue = resolve_dialogue(session)
            ingest_response = api_call(
                args.server_url,
                "POST",
                "/api/benchmark/halumem/ingest-session",
                {
                    "sessionId": backend_session_id,
                    "sessionLabel": session_label,
                    "sourceRefs": [
                        "dataset:halumem",
                        f"halumem_user:{user_id}",
                        f"halumem_session:{session_label}",
                    ],
                    "dialogue": dialogue,
                },
            )

            extracted_memory_items = ingest_response.get("extractedMemories", [])
            current_memory_items = ingest_response.get("currentMemories", [])
            state_memory_items = select_state_memory_items(current_memory_items, session_label)

            extracted_memories = []
            for item in extracted_memory_items:
                text = render_memory_item_text(item)
                if text:
                    extracted_memories.append(text)

            current_memories = []
            for item in state_memory_items:
                text = render_memory_item_text(item)
                if text:
                    current_memories.append(text)

            extraction_judgement = api_call(
                args.server_url,
                "POST",
                "/api/benchmark/halumem/judge/memory",
                {
                    "goldMemoryPoints": memory_points,
                    "systemMemoryItems": extracted_memories,
                },
            )
            state_judgement = api_call(
                args.server_url,
                "POST",
                "/api/benchmark/halumem/judge/memory",
                {
                    "goldMemoryPoints": memory_points,
                    "systemMemoryItems": current_memories,
                },
            )

            question_outputs: list[dict[str, Any]] = []
            for question_item in resolve_questions(session):
                answer_response = api_call(
                    args.server_url,
                    "POST",
                    "/api/benchmark/halumem/answer",
                    {
                        "sessionId": backend_session_id,
                        "question": question_item["question"],
                    },
                )
                qa_judgement = api_call(
                    args.server_url,
                    "POST",
                    "/api/benchmark/halumem/judge/qa",
                    {
                        "question": question_item["question"],
                        "groundTruth": question_item["ground_truth"],
                        "systemAnswer": answer_response.get("answer", ""),
                        "referenceContext": question_item["context"],
                        "retrievedContext": answer_response.get("retrievedContext", ""),
                    },
                )
                question_outputs.append(
                    {
                        "question": question_item["question"],
                        "ground_truth": question_item["ground_truth"],
                        "context": question_item["context"],
                        "system_response": answer_response.get("answer", ""),
                        "thinking": answer_response.get("thinking", ""),
                        "retrieved_context": answer_response.get("retrievedContext", ""),
                        "source_session_ids": answer_response.get("rankedSourceSessionIds", []),
                        "qa_judgement": qa_judgement,
                    }
                )

            sessions_output.append(
                {
                    "session_id": session_label,
                    "memory_points": memory_points,
                    "dialogue": dialogue,
                    "extracted_memories": extracted_memories,
                    "extraction_judgement": extraction_judgement,
                    "updated_memories_from_system": current_memories,
                    "state_memory_selection": {
                        "session_ref": f"halumem_session:{session_label}",
                        "selected_count": len(state_memory_items),
                        "all_current_count": len(current_memory_items),
                    },
                    "state_judgement": state_judgement,
                    "questions": question_outputs,
                }
            )

        user_output = {
            "user_id": user_id,
            "backend_session_id": backend_session_id,
            "sessions": sessions_output,
        }
        append_jsonl(artifact_path, user_output)
        completed_user_ids.add(user_id)
        processed_users += 1
        write_progress(progress_path, run_id, dataset_path, total_users, processed_users, user_id, started_at)

        elapsed_minutes = (time.time() - started_at) / 60.0
        log(f"[{run_id}] processed {processed_users}/{total_users} users ({processed_users / total_users:.1%}) | last={user_id} | elapsed={elapsed_minutes:.1f}m")

    results = []
    for line in artifact_path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped:
            results.append(json.loads(stripped))

    summary = compute_summary(results, run_id, dataset_path)
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    if progress_path.exists():
        progress_path.unlink()

    log(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
