# Local Prod Simulation Guide

This guide exercises the real local `dev` path:

- REST `POST /api/batch/customer/import`
- RabbitMQ enqueue + consume
- correlation lookup
- async Spring Batch launch
- status polling
- audit report reads
- queue backlog / DLQ visibility

It uses `scripts/local_prod_sim.py`, which:

- generates different CSV files per request
- mixes valid rows, policy-filter rows, and parse-skip rows
- sends imports at a controlled rate
- records correlation ids and request metadata under `target/prod-sim/...`
- reads RabbitMQ queue depth and can peek queued messages
- samples random correlation ids from a run and resolves status/report data

## 1. Preconditions

Start Oracle XE:

```bash
docker run -d \
  --name oracle-xe \
  -e ORACLE_PASSWORD=oracle123 \
  -e APP_USER=batch_user \
  -e APP_USER_PASSWORD=batch_pass \
  -p 1521:1521 \
  gvenzl/oracle-xe:21-slim
```

Start RabbitMQ with the management UI:

```bash
docker compose -f docker-compose.rabbitmq.yml up -d
```

UI:

- `http://localhost:15672`
- user: `guest`
- password: `guest`

Start the application with the real async + RabbitMQ profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Wait until the app is fully started before sending load.

## 2. Safe local rate

The current code launches jobs on `SimpleAsyncTaskExecutor`, which is unbounded.
That means your laptop limit is not the HTTP server or RabbitMQ; it is how many
batch threads + Oracle sessions your machine can absorb before it starts thrashing.

Ask the script for the conservative local rate:

```bash
python3 scripts/local_prod_sim.py recommend-rate
```

On this machine, the script reported:

- CPU cores: `10`
- local red-line estimate: `5 req/sec`
- recommended safe steady rate: `3 req/sec`

Use the recommended rate as the steady load. Do not jump above the red-line
estimate unless you deliberately want to stress the laptop rather than validate flows.

## 3. Terminal layout

Use four terminals.

### Terminal A: application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Terminal B: queue watch

```bash
python3 scripts/local_prod_sim.py queue-stats --watch --interval-seconds 0.5
```

This shows:

- `ready`: messages still buffered in RabbitMQ and waiting to be consumed
- `unacked`: messages delivered but not yet acknowledged
- `total`: ready + unacked
- `customer.import.dlq`: dead-letter queue depth

### Terminal C: load sender

Recommended first run:

```bash
python3 scripts/local_prod_sim.py send \
  --rate 3 \
  --duration-seconds 120 \
  --initial-burst 12
```

What this does:

- sends a short burst first so queue backlog is visible
- then continues at `3 req/sec`
- generates different CSV files under `target/prod-sim/runs/<timestamp>/input/`
- writes request logs under `target/prod-sim/runs/<timestamp>/logs/`

### Terminal D: random inspection

After the sender prints the run directory, use it here:

```bash
python3 scripts/local_prod_sim.py sample-run \
  --run-dir target/prod-sim/runs/<timestamp> \
  --sample-size 8 \
  --include-report
```

This resolves random correlation ids from the run and prints:

- whether the job id is still pending
- current status (`STARTED`, `COMPLETED`, `FAILED`)
- read/write/skip/filter counters
- audit report totals and sampled rejection categories

## 4. What the sender generates

The sender rotates through multiple CSV profiles:

- `valid-small`
  - only valid rows
- `policy-heavy`
  - valid rows + many invalid emails
  - drives `filterCount` and `POLICY_FILTER` audit rows
- `parse-mixed`
  - valid rows + invalid emails + malformed lines
  - drives both `filterCount` and parse skips
- `mixed-large`
  - larger file to keep jobs alive longer
  - useful for overlapping imports and queue visibility

All files are different, and row ids are unique per request so Oracle upserts do
not collapse everything into a tiny repeated dataset.

## 5. How to see queued messages

### Queue depth

Use Terminal B:

```bash
python3 scripts/local_prod_sim.py queue-stats --watch --interval-seconds 0.5
```

If `ready > 0`, messages are buffered in RabbitMQ and have not yet been consumed.

### Peek queued payloads without removing them

```bash
python3 scripts/local_prod_sim.py peek-queue --count 5
```

This calls the RabbitMQ management API with requeue mode, so the messages stay on
the queue after inspection.

You can also inspect the same queue in the UI:

- Queues
- `customer.import.queue`
- `Get messages`

## 6. Manual flow checks

Pick one correlation id from:

- `target/prod-sim/runs/<timestamp>/logs/requests.jsonl`
- or the sender console output

### Resolve `jobExecutionId`

```bash
curl "http://localhost:8080/api/batch/customer/import/by-correlation/<correlationId>/job"
```

Expected:

- `404` while still queued / not yet persisted
- `200 {"jobExecutionId":...}` once the listener launches the job

### Poll status

```bash
curl "http://localhost:8080/api/batch/customer/import/<jobExecutionId>/status"
```

Expected:

- `200` for non-failed states
- `500` with the same JSON body when batch status is `FAILED`

Important fields:

- `readCount`
- `writeCount`
- `skipCount`
- `filterCount`
- `failures`

### Pull report

```bash
curl "http://localhost:8080/api/batch/customer/import/<jobExecutionId>/report?limit=20&offset=0"
```

Expected fields:

- `jobStatus`
- `totalRejectedRows`
- `rows[*].category`
- `rows[*].reason`

Typical categories:

- `PARSE_SKIP`
- `READ_SKIPPED`
- `PROCESS_SKIPPED`
- `WRITE_SKIPPED`
- `POLICY_FILTER`

## 7. Database checks

Use SQL Developer, DBeaver, or `sqlplus` against the Oracle XE container.

### Latest correlation mappings

```sql
SELECT CORRELATION_ID, JOB_EXECUTION_ID, CREATED_AT
FROM IMPORT_LAUNCH_CORRELATION
ORDER BY CREATED_AT DESC
FETCH FIRST 20 ROWS ONLY;
```

### Latest batch executions

```sql
SELECT JOB_EXECUTION_ID, STATUS, START_TIME, END_TIME, EXIT_CODE
FROM BATCH_JOB_EXECUTION
ORDER BY JOB_EXECUTION_ID DESC
FETCH FIRST 20 ROWS ONLY;
```

### Latest rejected rows

```sql
SELECT ID, JOB_EXECUTION_ID, CATEGORY, LINE_NUMBER, REASON
FROM IMPORT_REJECTED_ROW
ORDER BY ID DESC
FETCH FIRST 50 ROWS ONLY;
```

### Rejection summary by category

```sql
SELECT CATEGORY, COUNT(*) AS ROWS
FROM IMPORT_REJECTED_ROW
GROUP BY CATEGORY
ORDER BY CATEGORY;
```

### Rejection summary by job

```sql
SELECT JOB_EXECUTION_ID, CATEGORY, COUNT(*) AS ROWS
FROM IMPORT_REJECTED_ROW
GROUP BY JOB_EXECUTION_ID, CATEGORY
ORDER BY JOB_EXECUTION_ID DESC, CATEGORY;
```

## 8. What to expect during a good run

### HTTP/API

- sender requests return `202`
- response body has `correlationId`
- status is usually `QUEUED`

### RabbitMQ

- `customer.import.queue ready` rises during the initial burst
- then falls as the listener drains the queue
- `customer.import.dlq` stays at `0` in a healthy run

### Application logs

Look for:

- `Import command accepted`
- `Received customer import command`
- `Launched customer import job`
- batch `JOB STARTED` / `JOB FINISHED`

### Status/report

Across random jobs you should see a mix of:

- `COMPLETED` with `filterCount > 0`
- `COMPLETED` with `skipCount > 0`
- report rows with `POLICY_FILTER`
- report rows with parse-skip categories

## 9. If you want more visible queue backlog

Because the listener only launches jobs and acknowledges quickly, queue backlog can
be short-lived even when the machine is under real batch load.

Use one or more of these:

1. Keep the steady rate at the recommended value.
2. Increase the initial burst slightly:

```bash
python3 scripts/local_prod_sim.py send \
  --rate 3 \
  --duration-seconds 120 \
  --initial-burst 20
```

3. Keep Terminal B polling every `0.5` seconds.
4. Use `peek-queue` while the sender is still running.

If the laptop becomes noisy or Oracle slows down sharply, reduce the burst first,
then reduce the steady rate.

## 10. If you want a harsher stress run

This is still local, so do it only after the first clean run.

```bash
python3 scripts/local_prod_sim.py send \
  --rate 4 \
  --duration-seconds 90 \
  --initial-burst 15
```

Watch for:

- queue depth never returning to near zero
- rapidly growing `BATCH_JOB_EXECUTION` with long-running `STARTED` jobs
- Oracle slowdown or connection errors
- the JVM creating too many `batch-*` threads

If that happens, the next engineering change is not "send fewer requests forever";
it is to replace the unbounded async launcher with a bounded `ThreadPoolTaskExecutor`.

## 11. Useful script commands

### Dry run only

Generates files and request logs without calling the app:

```bash
python3 scripts/local_prod_sim.py send \
  --dry-run \
  --count 5 \
  --run-dir target/prod-sim/dry-run
```

### One-shot queue snapshot

```bash
python3 scripts/local_prod_sim.py queue-stats
```

### Peek the DLQ

```bash
python3 scripts/local_prod_sim.py peek-queue --queue customer.import.dlq --count 5
```

### Random sample without report calls

```bash
python3 scripts/local_prod_sim.py sample-run \
  --run-dir target/prod-sim/runs/<timestamp> \
  --sample-size 5
```

## 12. Files produced by the simulator

Under `target/prod-sim/runs/<timestamp>/`:

- `metadata.json`
  - run settings
- `input/*.csv`
  - generated request files
- `logs/requests.jsonl`
  - one JSON object per request
- `logs/requests.csv`
  - spreadsheet-friendly summary

That run directory is the main artifact to keep when you want to replay or inspect
a specific local simulation.
