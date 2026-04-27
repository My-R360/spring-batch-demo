#!/usr/bin/env python3
"""
Local production-style simulation tool for the customer import service.

It supports:
1. Recommending a safe local request rate.
2. Generating mixed CSV inputs and sending POST imports at a controlled rate.
3. Inspecting RabbitMQ queue depth and peeking queued messages via the management API.
4. Sampling random correlation ids from a run and resolving job status/report data.
"""

from __future__ import annotations

import argparse
import base64
import csv
import json
import math
import os
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_RABBIT_API_URL = "http://localhost:15672/api"
DEFAULT_RABBIT_USER = "guest"
DEFAULT_RABBIT_PASSWORD = "guest"
DEFAULT_QUEUE = "customer.import.queue"
DEFAULT_DLQ = "customer.import.dlq"
DEFAULT_RUN_ROOT = Path("target/prod-sim")
DEFAULT_REQUEST_TIMEOUT_SECONDS = 15.0
DEFAULT_QUEUE_PEEK_TRUNCATE = 20_000


@dataclass(frozen=True)
class LoadProfile:
    name: str
    valid_rows: int
    invalid_email_rows: int
    malformed_rows: int
    include_header: bool


@dataclass
class HttpResponse:
    status_code: int
    body: Any
    text: str


PROFILES: tuple[LoadProfile, ...] = (
    LoadProfile(
        name="valid-small",
        valid_rows=80,
        invalid_email_rows=0,
        malformed_rows=0,
        include_header=False,
    ),
    LoadProfile(
        name="policy-heavy",
        valid_rows=120,
        invalid_email_rows=45,
        malformed_rows=0,
        include_header=False,
    ),
    LoadProfile(
        name="parse-mixed",
        valid_rows=120,
        invalid_email_rows=20,
        malformed_rows=10,
        include_header=True,
    ),
    LoadProfile(
        name="mixed-large",
        valid_rows=420,
        invalid_email_rows=70,
        malformed_rows=12,
        include_header=True,
    ),
)

PROFILE_WEIGHTS = (0.20, 0.30, 0.25, 0.25)


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def parse_json_maybe(text: str) -> Any:
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return text


def build_auth_header(username: str, password: str) -> str:
    token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"


def http_json_request(
        method: str,
        url: str,
        *,
        timeout_seconds: float,
        body: Any | None = None,
        auth_header: str | None = None,
) -> HttpResponse:
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode("utf-8")
    if auth_header:
        headers["Authorization"] = auth_header

    request = urllib.request.Request(url, method=method.upper(), data=data, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            text = response.read().decode("utf-8")
            return HttpResponse(
                status_code=response.status,
                body=parse_json_maybe(text),
                text=text,
            )
    except urllib.error.HTTPError as error:
        text = error.read().decode("utf-8")
        return HttpResponse(
            status_code=error.code,
            body=parse_json_maybe(text),
            text=text,
        )
    except urllib.error.URLError as error:
        raise RuntimeError(f"{method.upper()} {url} failed: {error}") from error


def local_rate_threshold(cpu_count: int) -> int:
    # This service launches jobs on an unbounded SimpleAsyncTaskExecutor, so the local safe ceiling
    # should stay low even on bigger laptops. The threshold here is a conservative "red line",
    # not a throughput target.
    return max(2, min(6, max(1, cpu_count) // 2))


def recommended_safe_rate(cpu_count: int) -> int:
    return max(1, math.floor(local_rate_threshold(cpu_count) * 0.7))


def resolve_run_dir(run_root: Path, requested: str | None) -> Path:
    if requested:
        return Path(requested).expanduser().resolve()
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return (run_root / "runs" / timestamp).resolve()


def ensure_run_layout(run_dir: Path) -> tuple[Path, Path]:
    input_dir = run_dir / "input"
    logs_dir = run_dir / "logs"
    input_dir.mkdir(parents=True, exist_ok=True)
    logs_dir.mkdir(parents=True, exist_ok=True)
    return input_dir, logs_dir


def pick_profile(rng: random.Random) -> LoadProfile:
    return rng.choices(PROFILES, weights=PROFILE_WEIGHTS, k=1)[0]


def malformed_row(row_number: int, request_number: int) -> str:
    patterns = (
        "garbage",
        f"not-a-number,Req{request_number:05d}Broken{row_number:04d},broken{row_number}@example.com",
        f"{10_000 + row_number},only-two-columns",
        f"{20_000 + row_number},too,many,columns,here",
    )
    return patterns[row_number % len(patterns)]


def write_csv_for_request(
        csv_path: Path,
        profile: LoadProfile,
        request_number: int,
        rng: random.Random,
) -> dict[str, int]:
    base_id = 10_000_000 + (request_number * 10_000)
    total_rows = profile.valid_rows + profile.invalid_email_rows + profile.malformed_rows

    invalid_positions = set(rng.sample(range(total_rows), k=profile.invalid_email_rows))
    remaining_positions = [index for index in range(total_rows) if index not in invalid_positions]
    malformed_positions = set(rng.sample(remaining_positions, k=profile.malformed_rows))

    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        if profile.include_header:
            handle.write("id,name,email\n")

        for row_number in range(total_rows):
            if row_number in malformed_positions:
                handle.write(malformed_row(row_number, request_number) + "\n")
                continue

            customer_id = base_id + row_number
            name = f"Req{request_number:05d}User{row_number:04d}"
            if row_number in invalid_positions:
                email = f"broken{customer_id}.example.com"
            else:
                email = f"user{customer_id}@example.com"
            handle.write(f"{customer_id},{name},{email}\n")

    return {
        "dataRows": total_rows,
        "validRows": profile.valid_rows,
        "policyFilterRows": profile.invalid_email_rows,
        "parseSkipRows": profile.malformed_rows + (1 if profile.include_header else 0),
    }


def request_log_paths(logs_dir: Path) -> tuple[Path, Path]:
    return logs_dir / "requests.jsonl", logs_dir / "requests.csv"


def append_jsonl(path: Path, payload: dict[str, Any]) -> None:
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(payload, sort_keys=True) + "\n")


def load_request_records(run_dir: Path) -> list[dict[str, Any]]:
    jsonl_path = run_dir / "logs" / "requests.jsonl"
    if not jsonl_path.exists():
        raise FileNotFoundError(f"Run file not found: {jsonl_path}")
    records: list[dict[str, Any]] = []
    with jsonl_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    return records


def queue_stats_url(api_root: str, queue_name: str) -> str:
    return f"{api_root.rstrip('/')}/queues/%2F/{urllib.parse.quote(queue_name, safe='')}"


def queue_peek_url(api_root: str, queue_name: str) -> str:
    return f"{queue_stats_url(api_root, queue_name)}/get"


def print_rate_recommendation(_: argparse.Namespace) -> int:
    cpu_count = os.cpu_count() or 8
    threshold = local_rate_threshold(cpu_count)
    safe = recommended_safe_rate(cpu_count)
    print(f"Detected CPU cores      : {cpu_count}")
    print(f"Local red-line estimate : {threshold} import requests/sec")
    print(f"Recommended safe rate   : {safe} import requests/sec")
    print("")
    print("Why this is conservative:")
    print("- Rabbit listener concurrency is 1, but each message launches a batch job asynchronously.")
    print("- The job launcher uses SimpleAsyncTaskExecutor, so local threads are effectively unbounded.")
    print("- Oracle XE, the JVM, and RabbitMQ share the same laptop resources.")
    print("")
    print("Start at the recommended safe rate. Raise only after observing queue depth, batch thread count,")
    print("and Oracle stability under load.")
    return 0


def write_metadata(run_dir: Path, payload: dict[str, Any]) -> None:
    path = run_dir / "metadata.json"
    with path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, sort_keys=True)
        handle.write("\n")


def send_load(args: argparse.Namespace) -> int:
    cpu_count = os.cpu_count() or 8
    threshold = local_rate_threshold(cpu_count)
    recommended = recommended_safe_rate(cpu_count)
    if args.rate > threshold:
        print(
            f"Warning: requested rate {args.rate}/sec is above the local red-line estimate {threshold}/sec.",
            file=sys.stderr,
        )
    elif args.rate > recommended:
        print(
            f"Warning: requested rate {args.rate}/sec is above the recommended safe rate {recommended}/sec.",
            file=sys.stderr,
        )

    run_dir = resolve_run_dir(DEFAULT_RUN_ROOT, args.run_dir)
    input_dir, logs_dir = ensure_run_layout(run_dir)
    jsonl_path, csv_path = request_log_paths(logs_dir)
    rng = random.Random(args.seed)

    total_requests = args.count if args.count is not None else args.rate * args.duration_seconds
    if total_requests <= 0:
        raise ValueError("Total request count must be positive.")

    write_metadata(
        run_dir,
        {
            "createdAt": utc_now_iso(),
            "baseUrl": args.base_url,
            "count": total_requests,
            "durationSeconds": args.duration_seconds,
            "dryRun": args.dry_run,
            "initialBurst": args.initial_burst,
            "queue": args.queue,
            "deadLetterQueue": args.dead_letter_queue,
            "rate": args.rate,
            "recommendedSafeRate": recommended,
            "localRateThreshold": threshold,
            "seed": args.seed,
        },
    )

    csv_headers = (
        "requestNumber",
        "submittedAt",
        "profile",
        "filePath",
        "inputFile",
        "httpStatus",
        "correlationId",
        "enqueueStatus",
        "jobExecutionId",
        "validRows",
        "policyFilterRows",
        "parseSkipRows",
        "error",
    )
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(csv_headers)

    start = time.monotonic()
    first_steady_request = args.initial_burst
    accepted_count = 0
    error_count = 0

    print(f"Run directory            : {run_dir}")
    print(f"Recommended safe rate    : {recommended} req/sec")
    print(f"Using steady rate        : {args.rate} req/sec")
    print(f"Initial burst            : {args.initial_burst}")
    print(f"Total requests           : {total_requests}")
    if args.dry_run:
        print("Mode                     : dry-run (files generated, no HTTP requests)")
    print("")

    for request_index in range(total_requests):
        if request_index >= first_steady_request and args.rate > 0:
            steady_index = request_index - first_steady_request
            target_time = start + (steady_index / args.rate)
            sleep_seconds = target_time - time.monotonic()
            if sleep_seconds > 0:
                time.sleep(sleep_seconds)

        profile = pick_profile(rng)
        csv_name = f"req-{request_index + 1:05d}-{profile.name}.csv"
        csv_path_input = input_dir / csv_name
        csv_stats = write_csv_for_request(csv_path_input, profile, request_index + 1, rng)
        input_file = f"file:{csv_path_input.resolve()}"

        response_status = 0
        correlation_id = None
        enqueue_status = None
        job_execution_id = None
        error_text = ""

        if args.dry_run:
            response_status = 0
            error_text = "dry-run"
        else:
            import_url = (
                f"{args.base_url.rstrip('/')}/api/batch/customer/import?"
                + urllib.parse.urlencode({"inputFile": input_file})
            )
            response = http_json_request(
                "POST",
                import_url,
                timeout_seconds=args.timeout_seconds,
            )
            response_status = response.status_code
            if isinstance(response.body, dict):
                correlation_id = response.body.get("correlationId")
                enqueue_status = response.body.get("status")
                job_execution_id = response.body.get("jobExecutionId")
                if response_status >= 400:
                    error_text = str(response.body.get("detail") or response.body)
            else:
                error_text = response.text

        accepted_count += 1 if response_status in (0, 202) else 0
        error_count += 0 if response_status in (0, 202) else 1

        record = {
            "requestNumber": request_index + 1,
            "submittedAt": utc_now_iso(),
            "profile": profile.name,
            "filePath": str(csv_path_input.resolve()),
            "inputFile": input_file,
            "httpStatus": response_status,
            "correlationId": correlation_id,
            "enqueueStatus": enqueue_status,
            "jobExecutionId": job_execution_id,
            **csv_stats,
            "error": error_text,
        }

        append_jsonl(jsonl_path, record)
        with (run_dir / "logs" / "requests.csv").open("a", encoding="utf-8", newline="") as handle:
            writer = csv.writer(handle)
            writer.writerow([
                record["requestNumber"],
                record["submittedAt"],
                record["profile"],
                record["filePath"],
                record["inputFile"],
                record["httpStatus"],
                record["correlationId"],
                record["enqueueStatus"],
                record["jobExecutionId"],
                record["validRows"],
                record["policyFilterRows"],
                record["parseSkipRows"],
                record["error"],
            ])

        if (request_index + 1) % 10 == 0 or request_index == total_requests - 1:
            print(
                f"[{request_index + 1:04d}/{total_requests}] "
                f"profile={profile.name:<12} http={response_status:<3} "
                f"corr={correlation_id or '-'}"
            )

    print("")
    print(f"Accepted/enqueued: {accepted_count}")
    print(f"HTTP errors      : {error_count}")
    print(f"Request log      : {jsonl_path}")
    print(f"CSV summary      : {run_dir / 'logs' / 'requests.csv'}")
    return 0


def queue_stats(args: argparse.Namespace) -> int:
    auth_header = build_auth_header(args.rabbit_user, args.rabbit_password)
    queue_names = (args.queue, args.dead_letter_queue)

    def fetch_once() -> None:
        timestamp = utc_now_iso()
        print(timestamp)
        for queue_name in queue_names:
            response = http_json_request(
                "GET",
                queue_stats_url(args.rabbit_api_url, queue_name),
                timeout_seconds=args.timeout_seconds,
                auth_header=auth_header,
            )
            if response.status_code != 200 or not isinstance(response.body, dict):
                print(f"  {queue_name}: HTTP {response.status_code} {response.text}")
                continue

            body = response.body
            message_stats = body.get("message_stats") or {}
            publish_details = message_stats.get("publish_details") or {}
            deliver_details = message_stats.get("deliver_get_details") or {}
            ack_details = message_stats.get("ack_details") or {}
            print(
                "  "
                f"{queue_name}: "
                f"state={body.get('state')} "
                f"consumers={body.get('consumers', 0)} "
                f"ready={body.get('messages_ready', 0)} "
                f"unacked={body.get('messages_unacknowledged', 0)} "
                f"total={body.get('messages', 0)} "
                f"publish_rate={publish_details.get('rate', 0):.2f} "
                f"deliver_rate={deliver_details.get('rate', 0):.2f} "
                f"ack_rate={ack_details.get('rate', 0):.2f}"
            )
        print("")

    if args.watch:
        try:
            while True:
                fetch_once()
                time.sleep(args.interval_seconds)
        except KeyboardInterrupt:
            return 0
    fetch_once()
    return 0


def queue_peek(args: argparse.Namespace) -> int:
    auth_header = build_auth_header(args.rabbit_user, args.rabbit_password)
    response = http_json_request(
        "POST",
        queue_peek_url(args.rabbit_api_url, args.queue),
        timeout_seconds=args.timeout_seconds,
        auth_header=auth_header,
        body={
            "count": args.count,
            "ackmode": "ack_requeue_true",
            "encoding": "auto",
            "truncate": args.truncate,
        },
    )
    if response.status_code != 200 or not isinstance(response.body, list):
        print(f"Queue peek failed: HTTP {response.status_code} {response.text}", file=sys.stderr)
        return 1

    if not response.body:
        print(f"No messages available in {args.queue}.")
        return 0

    print(f"Peeked {len(response.body)} message(s) from {args.queue}:")
    for index, message in enumerate(response.body, start=1):
        payload_raw = message.get("payload")
        payload = parse_json_maybe(payload_raw) if isinstance(payload_raw, str) else payload_raw
        if isinstance(payload, dict):
            corr = payload.get("correlationId")
            input_file = payload.get("inputFile")
            schema_version = payload.get("schemaVersion")
        else:
            corr = None
            input_file = None
            schema_version = None
        print(
            f"[{index}] routingKey={message.get('routing_key')} "
            f"redelivered={message.get('redelivered')} "
            f"remainingAfterPeek={message.get('message_count')}"
        )
        if corr or input_file:
            print(f"    correlationId={corr}")
            print(f"    inputFile={input_file}")
            print(f"    schemaVersion={schema_version}")
        else:
            print(f"    payload={payload}")
    return 0


def by_correlation_url(base_url: str, correlation_id: str) -> str:
    return (
        f"{base_url.rstrip('/')}/api/batch/customer/import/by-correlation/"
        f"{urllib.parse.quote(correlation_id)}/job"
    )


def status_url(base_url: str, job_execution_id: int) -> str:
    return f"{base_url.rstrip('/')}/api/batch/customer/import/{job_execution_id}/status"


def report_url(base_url: str, job_execution_id: int, limit: int) -> str:
    return (
        f"{base_url.rstrip('/')}/api/batch/customer/import/{job_execution_id}/report?"
        + urllib.parse.urlencode({"limit": limit, "offset": 0})
    )


def sample_run(args: argparse.Namespace) -> int:
    records = load_request_records(Path(args.run_dir).resolve())
    accepted = [record for record in records if record.get("correlationId")]
    if not accepted:
        print("No accepted requests with correlation ids found in this run.", file=sys.stderr)
        return 1

    rng = random.Random(args.seed)
    sample_size = min(args.sample_size, len(accepted))
    chosen = rng.sample(accepted, k=sample_size)

    for record in chosen:
        corr = record["correlationId"]
        print(f"Request #{record['requestNumber']} profile={record['profile']} correlationId={corr}")
        correlation_response = http_json_request(
            "GET",
            by_correlation_url(args.base_url, corr),
            timeout_seconds=args.timeout_seconds,
        )
        if correlation_response.status_code == 404:
            print("  jobExecutionId: pending (still queued, launching, or not yet persisted)")
            print("")
            continue

        if correlation_response.status_code != 200 or not isinstance(correlation_response.body, dict):
            print(f"  correlation lookup failed: HTTP {correlation_response.status_code} {correlation_response.text}")
            print("")
            continue

        job_execution_id = correlation_response.body.get("jobExecutionId")
        print(f"  jobExecutionId: {job_execution_id}")

        status_response = http_json_request(
            "GET",
            status_url(args.base_url, int(job_execution_id)),
            timeout_seconds=args.timeout_seconds,
        )
        if isinstance(status_response.body, dict):
            body = status_response.body
            print(
                "  status: "
                f"http={status_response.status_code} "
                f"batch={body.get('status')} "
                f"read={body.get('readCount')} "
                f"write={body.get('writeCount')} "
                f"skip={body.get('skipCount')} "
                f"filter={body.get('filterCount')}"
            )
            failures = body.get("failures") or []
            if failures:
                print(f"  failures: {failures}")
        else:
            print(f"  status lookup failed: HTTP {status_response.status_code} {status_response.text}")

        if args.include_report:
            report_response = http_json_request(
                "GET",
                report_url(args.base_url, int(job_execution_id), args.report_limit),
                timeout_seconds=args.timeout_seconds,
            )
            if isinstance(report_response.body, dict):
                rows = report_response.body.get("rows") or []
                categories: dict[str, int] = {}
                for row in rows:
                    category = row.get("category")
                    if category:
                        categories[category] = categories.get(category, 0) + 1
                print(
                    "  report: "
                    f"http={report_response.status_code} "
                    f"totalRejectedRows={report_response.body.get('totalRejectedRows')} "
                    f"sampleCategories={categories or '{}'}"
                )
            else:
                print(f"  report lookup failed: HTTP {report_response.status_code} {report_response.text}")
        print("")

    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Local production-style simulator for the customer import service."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    parser_rate = subparsers.add_parser(
        "recommend-rate",
        help="Print the conservative local request-rate recommendation.",
    )
    parser_rate.set_defaults(func=print_rate_recommendation)

    parser_send = subparsers.add_parser(
        "send",
        help="Generate mixed CSV files and POST imports at a controlled rate.",
    )
    parser_send.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser_send.add_argument("--rate", type=int, default=recommended_safe_rate(os.cpu_count() or 8))
    parser_send.add_argument("--duration-seconds", type=int, default=60)
    parser_send.add_argument("--count", type=int, default=None)
    parser_send.add_argument("--initial-burst", type=int, default=10)
    parser_send.add_argument("--run-dir", default=None)
    parser_send.add_argument("--queue", default=DEFAULT_QUEUE)
    parser_send.add_argument("--dead-letter-queue", default=DEFAULT_DLQ)
    parser_send.add_argument("--seed", type=int, default=42)
    parser_send.add_argument("--timeout-seconds", type=float, default=DEFAULT_REQUEST_TIMEOUT_SECONDS)
    parser_send.add_argument("--dry-run", action="store_true")
    parser_send.set_defaults(func=send_load)

    parser_queue = subparsers.add_parser(
        "queue-stats",
        help="Show RabbitMQ queue depth, consumer count, and message rates.",
    )
    parser_queue.add_argument("--rabbit-api-url", default=DEFAULT_RABBIT_API_URL)
    parser_queue.add_argument("--rabbit-user", default=DEFAULT_RABBIT_USER)
    parser_queue.add_argument("--rabbit-password", default=DEFAULT_RABBIT_PASSWORD)
    parser_queue.add_argument("--queue", default=DEFAULT_QUEUE)
    parser_queue.add_argument("--dead-letter-queue", default=DEFAULT_DLQ)
    parser_queue.add_argument("--timeout-seconds", type=float, default=DEFAULT_REQUEST_TIMEOUT_SECONDS)
    parser_queue.add_argument("--watch", action="store_true")
    parser_queue.add_argument("--interval-seconds", type=float, default=1.0)
    parser_queue.set_defaults(func=queue_stats)

    parser_peek = subparsers.add_parser(
        "peek-queue",
        help="Peek queued RabbitMQ command payloads without removing them.",
    )
    parser_peek.add_argument("--rabbit-api-url", default=DEFAULT_RABBIT_API_URL)
    parser_peek.add_argument("--rabbit-user", default=DEFAULT_RABBIT_USER)
    parser_peek.add_argument("--rabbit-password", default=DEFAULT_RABBIT_PASSWORD)
    parser_peek.add_argument("--queue", default=DEFAULT_QUEUE)
    parser_peek.add_argument("--count", type=int, default=5)
    parser_peek.add_argument("--truncate", type=int, default=DEFAULT_QUEUE_PEEK_TRUNCATE)
    parser_peek.add_argument("--timeout-seconds", type=float, default=DEFAULT_REQUEST_TIMEOUT_SECONDS)
    parser_peek.set_defaults(func=queue_peek)

    parser_sample = subparsers.add_parser(
        "sample-run",
        help="Resolve random correlation ids from a run and print status/report snapshots.",
    )
    parser_sample.add_argument("--run-dir", required=True)
    parser_sample.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser_sample.add_argument("--sample-size", type=int, default=5)
    parser_sample.add_argument("--seed", type=int, default=42)
    parser_sample.add_argument("--timeout-seconds", type=float, default=DEFAULT_REQUEST_TIMEOUT_SECONDS)
    parser_sample.add_argument("--include-report", action="store_true")
    parser_sample.add_argument("--report-limit", type=int, default=10)
    parser_sample.set_defaults(func=sample_run)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        return args.func(args)
    except KeyboardInterrupt:
        return 130
    except Exception as error:  # noqa: BLE001
        print(f"Error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
