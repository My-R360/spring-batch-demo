# Runbook: Spring Batch CSV → Oracle (Docker XE)

## 1) Prerequisites

- **Java 21** (set `JAVA_HOME` to Java 21 — avoid running with Java 25+ as Byte Buddy/Mockito compatibility issues may surface)
- Docker
- (Optional) IntelliJ Database tool window / SQL client

This repo uses the Maven Wrapper: `./mvnw`.

### 1.1) Optional: Slidev architecture deck

Optional Slidev deck: use a local `slidev/` checkout (the folder is **gitignored**). From that directory: `npm install`, then `npm run dev` to preview. See `slidev/README.md` and `slidev/PRESENTATION.md` when present.

## 2) Oracle XE in Docker

### 2.1 Container status

You already have:

```text
oracle-db  (gvenzl/oracle-xe)  0.0.0.0:1521->1521
```

### 2.2 Create the application user (one-time)

`sqlplus` is inside the Oracle container; you do **not** need it on macOS.

```bash
docker exec -it oracle-db bash
sqlplus / as sysdba
```

In SQL*Plus, switch to the PDB and create the user:

```sql
SHOW CON_NAME;
ALTER SESSION SET CONTAINER = XEPDB1;
SHOW CON_NAME;

CREATE USER batch_user IDENTIFIED BY batch_pass;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE TRIGGER TO batch_user;
ALTER USER batch_user QUOTA UNLIMITED ON USERS;

SELECT username FROM dba_users WHERE username = 'BATCH_USER';
```

## 3) App configuration (where the DB settings live)

- `src/main/resources/application.properties`
  - default profile (production-safe): does **not** auto-create tables
- `src/main/resources/application-dev.properties`
  - dev profile: initializes schema and enables extra logs
  - Oracle `schema.sql` uses PL/SQL blocks; `spring.sql.init.separator=^^^ END ORACLE DDL ^^^` so Spring does not split on `;` inside `BEGIN…END` or strip a delimiter line that starts with `--` (treated as a comment). Without that, only `CUSTOMER` might be created.

Oracle connection used by the app:

- URL: `jdbc:oracle:thin:@//localhost:1521/XEPDB1`
- user: `batch_user`
- pass: `batch_pass`

## 4) Run the application

### 4.0 RabbitMQ (Phase 3, required for `dev` profile)

The `dev` profile sets `app.messaging.customer-import.enabled=true`. `application-dev.yaml` sets `spring.autoconfigure.exclude` to an **empty list** so the base `application.properties` exclusion of `RabbitAutoConfiguration` is cleared and the app can connect to RabbitMQ on startup.

```bash
docker compose -f docker-compose.rabbitmq.yml up -d
# Management UI: http://localhost:15672  (guest / guest)
```

Without a broker, the app will fail to start in `dev`. Profiles `test` and `audit-it` keep messaging **disabled** and exclude `RabbitAutoConfiguration` (no broker needed).

### 4.1 Dev mode (recommended while learning/testing)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

On startup in `dev` you should see:
- Oracle user diagnostics (connected user)
- SQL init debug logs

### 4.2 Non-dev mode (no schema auto-init)

```bash
./mvnw spring-boot:run
```

In this mode you must create tables yourself (or via migrations).

### 4.3 In-memory H2 smoke (`audit-it` profile)

Runs the app with **embedded** Spring Batch metadata + `CUSTOMER` + `IMPORT_REJECTED_ROW` on **H2** (no Docker Oracle). Imports use a **no-op** `CustomerUpsertPort` so you can exercise REST + batch + audit without Oracle `MERGE` syntax.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=audit-it
```

Quick API check (after Tomcat is up) using the bundled **Phase 2** sample (policy filter + parse skips):

```bash
curl -s -X POST "http://localhost:8080/api/batch/customer/import?inputFile=classpath:customers-phase2-audit-sample.csv"
# → 202  {"correlationId":"…","status":"STARTED","jobExecutionId":1}  (messaging off: job id returned immediately)

curl -s "http://localhost:8080/api/batch/customer/import/1/status" | jq .
curl -s "http://localhost:8080/api/batch/customer/import/1/report?limit=20&offset=0" | jq .
```

## 5) Use Postman (import API)

The import API is **asynchronous** — POST returns **202 Accepted** with a **`CustomerImportEnqueueResponse`** JSON body:

- **`dev` (RabbitMQ on):** `{"correlationId":"<uuid>","status":"QUEUED","jobExecutionId":null}` — then poll `GET .../by-correlation/{correlationId}/job` until you receive `{"jobExecutionId":N}`, then poll status/report as before.
- **`audit-it` / `test` (messaging off):** `{"correlationId":"<uuid>","status":"STARTED","jobExecutionId":N}` — you can poll status immediately with `N`.

Bundled Postman collection (v2.1 JSON): [`docs/postman/customer-import-api.json`](docs/postman/customer-import-api.json) — import into Postman and set `baseUrl`, `correlationId`, `jobExecutionId` variables as you go.

### 5.1 Endpoints

| Method | URL | Description |
|--------|-----|-------------|
| `POST` | `/api/batch/customer/import?inputFile=…` | Accept import (**202**); body: `correlationId`, `status` (`QUEUED` or `STARTED`), optional `jobExecutionId`; **400** if `inputFile` is missing/blank or points at a resource the app cannot read; **503** if broker unreachable (`dev`) |
| `GET` | `/api/batch/customer/import/by-correlation/{correlationId}/job` | Returns **`{"jobExecutionId":N}`** when the consumer has launched the job; **404** until mapped; **400** if `correlationId` is not a valid UUID |
| `GET` | `/api/batch/customer/import/{jobExecutionId}/status` | Poll job status/progress (includes `filterCount`, `rejectedSample`) |
| `GET` | `/api/batch/customer/import/{jobExecutionId}/report?limit=&offset=` | Paginated per-row audit from `IMPORT_REJECTED_ROW` (`PARSE_SKIP`, `READ_SKIPPED`, `PROCESS_SKIPPED`, `WRITE_SKIPPED`, `POLICY_FILTER`) |

### 5.2 Import a CSV bundled in the jar (`inputFile` required)

```bash
curl -s -X POST "http://localhost:8080/api/batch/customer/import?inputFile=classpath:customers.csv"
# dev + RabbitMQ →  {"correlationId":"…","status":"QUEUED","jobExecutionId":null}
# audit-it / no broker → {"correlationId":"…","status":"STARTED","jobExecutionId":1}
```

Omitting `inputFile` or sending a blank value returns **400** (`ProblemDetail`, title `Missing input file`). A non-existent or unreadable resource also returns **400** (`ProblemDetail`, title `Invalid input file`) before any RabbitMQ command is published. Read skips (`skipCount`), policy-filtered rows (`filterCount`), and optional process/write skips are reflected in status counters; persisted reasons and raw fields appear in `rejectedSample` (first rows) and in the **report** endpoint. **Audit inserts are not swallowed:** if `IMPORT_REJECTED_ROW` insert fails, the step fails (check DDL vs `schema.sql` and `INSERT` privilege).

**Counters vs `CUSTOMER`:** `readCount` counts successful reader items; `writeCount` is rows upserted; `skipCount` is Spring Batch read (and, when configured, process/write) skips; `filterCount` is processor returned `null` (policy filter). Rows that never become a `Customer` instance (read failure) only appear in audit when the exception is **skippable** and the skip listener runs—extend `.skip(...)` on `customerStep` if you need more exception types audited.

### 5.3 Poll job status

```bash
curl "http://localhost:8080/api/batch/customer/import/1/status"
# → 200  {"jobExecutionId":1,"status":"COMPLETED","failures":[],"readCount":6,"writeCount":5,"skipCount":0,"filterCount":0,"rejectedSample":[]}

curl "http://localhost:8080/api/batch/customer/import/1/report?limit=50&offset=0"
# → 200  {"jobExecutionId":1,"jobStatus":"COMPLETED","totalRejectedRows":0,"rows":[]}
```

Status values: `STARTING` → `STARTED` → `COMPLETED` or `FAILED`. Returns **404** for unknown IDs. When `status` is **`FAILED`**, the API returns **500** with the same **`CustomerImportResult`** JSON body (counters + `failures`) so clients can still parse the payload while HTTP status reflects hard failure.

When `status` is `FAILED`, the `failures` list is filled from **persisted** Spring Batch exit messages on the job and on any failed steps (the same text stored in batch metadata and visible after a process restart). It is not derived from transient in-memory exception lists, so polling remains accurate across JVMs.

### 5.4 Import a different CSV

```bash
curl -X POST "http://localhost:8080/api/batch/customer/import?inputFile=classpath:customers-01.csv"
```

### 5.5 Import ANY local file on your Mac

Use the `file:` resource prefix with an **absolute path**:

```bash
curl -X POST "http://localhost:8080/api/batch/customer/import?inputFile=file:/Users/shubham.s/customers-any.csv"
```

Notes:
- Path must be accessible to the app process (your Mac filesystem).
- Plain absolute paths also work locally, but `file:/absolute/path.csv` keeps the resource type explicit.
- In local development, the publisher stages external local files into `target/classes/customer-imports/` and changes `inputFile` to `classpath:customer-imports/<correlationId>-<file>.csv` before RabbitMQ/direct job launch.
- RabbitMQ sends only `{correlationId,inputFile,schemaVersion}`. It does **not** send the CSV bytes; after staging, `inputFile` is the staged classpath location.
- If your path has spaces, URL-encode it in Postman or curl.

## 6) How to verify (Oracle)

### 6.1 Connect as `batch_user` inside the container

```bash
docker exec -it oracle-db bash
sqlplus batch_user/batch_pass@//localhost:1521/XEPDB1
```

### 6.2 Check tables

```sql
SELECT table_name FROM user_tables WHERE table_name IN ('CUSTOMER', 'IMPORT_REJECTED_ROW', 'IMPORT_LAUNCH_CORRELATION', 'BATCH_JOB_EXECUTION');
```

### 6.3 Check imported rows

```sql
SELECT * FROM CUSTOMER ORDER BY ID;
```

Expected behavior:
- Rows with invalid email (no `@`) are filtered (not written).
- Names are uppercased.
- Reruns do not fail on duplicate IDs because writer uses `MERGE` (upsert).

### 6.4 Check Spring Batch metadata (did the job actually run?)

```sql
SELECT JOB_EXECUTION_ID, STATUS, START_TIME, END_TIME
FROM BATCH_JOB_EXECUTION
ORDER BY JOB_EXECUTION_ID DESC;
```

## 7) Internal flow (what calls what)

### 7.1 Request → command → job launch (async)

**Profile `dev` (RabbitMQ enabled)**

1. Client calls `POST /api/batch/customer/import?inputFile=…` (parameter required).
2. Controller validates `inputFile` and confirms the resource exists/readable, generates a **UUID `correlationId`**, builds `CustomerImportCommand`, calls `CustomerImportCommandPublisher.publish`.
3. `AmqpCustomerImportCommandPublisher` stages local filesystem inputs under `target/classes/customer-imports/` and sends JSON to exchange `customer.import.commands` with routing key `customer.import.command`; bundled `classpath:` inputs pass through unchanged.
4. Controller returns **202** with `QUEUED` and `jobExecutionId: null`.
5. `CustomerImportJobLaunchListener` receives the message (prefetch 1, JSON converter, retry + DLQ on the container factory).
6. Listener calls `CustomerImportUseCase.launchImport(inputFile)` → validates/stages as a fallback, then async `JobLauncher.run()` as in Phase 1.
7. Listener inserts `IMPORT_LAUNCH_CORRELATION` (`correlation_id` → `job_execution_id`).
8. Client polls `GET .../by-correlation/{correlationId}/job` until **200**, then polls `GET .../{jobExecutionId}/status` as before.

**Profiles `test` / `audit-it` (messaging disabled)**

1–4. Same entry, but `DirectCustomerImportCommandPublisher` stages local filesystem inputs, calls `launchImport` on the HTTP thread, and registers correlation before returning **202** with `STARTED` and a non-null `jobExecutionId` (no RabbitMQ).

### 7.2 Job → Fault-tolerant step → chunk loop

`customerJob` runs a single fault-tolerant step `customerStep` (chunk size 10):

- Repeatedly:
  - Reader reads 1 CSV line → `Customer`
  - Processor validates/transforms → `Customer` or `null` (filtered)
  - Writer upserts the chunk into Oracle
- **Retry**: `TransientDataAccessException` retried up to 3 times with exponential backoff (1s → 2s → 4s, max 8s)
- **Skip**: `FlatFileParseException` (malformed CSV rows) skipped, up to 100 per job

### 7.3 Status polling

1. Client calls `GET /api/batch/customer/import/{id}/status`
2. Controller calls `CustomerImportUseCase.getImportStatus(id)`
3. Infrastructure uses `JobExplorer` to look up the `JobExecution` and its `StepExecution` counts
4. Returns `{status, readCount, writeCount, skipCount, filterCount, rejectedSample, failures}` and can call `getImportAuditReport` for paginated rows from `IMPORT_REJECTED_ROW`

### 7.4 Key code locations

- Presentation (API): `.../presentation/api/BatchJobController.java`
- Presentation (errors): `.../presentation/api/exceptions/BatchJobApiExceptionHandler.java`
- Application ports + DTO: `.../application/customer/port/CustomerImportUseCase.java`, `CustomerUpsertPort.java`, `ImportAuditPort.java`; `.../application/customer/dto/CustomerImportResult.java`, `ImportAuditReport.java`
- Application import input / errors: `.../application/customer/CustomerImportInputFile.java`; `.../application/customer/exceptions/` (`MissingInputFileException`, `ImportJobLaunchException`, `ImportCommandPublishException`, `InvalidCorrelationIdException`)
- Phase 3 messaging: `.../application/customer/dto/CustomerImportCommand.java`, `CustomerImportEnqueueResponse.java`; ports `CustomerImportCommandPublisher`, `ImportLaunchCorrelationPort`; infra `.../adapter/messaging/*`, `.../config/messaging/CustomerImportRabbitConfig.java`, `JdbcImportLaunchCorrelationAdapter.java`
- Domain policy: `.../domain/customer/policy/`
- Job/Step + reader **configuration**: `.../infrastructure/batch/config/CustomerImportJobConfig.java`, `CustomerCsvItemReaderConfig.java`, `CustomerImportAuditListenerConfig.java`
- Batch **adapters** + listeners: `.../infrastructure/adapter/batch/` (includes `CustomerImportAuditStepListener` for skip/process audit)
- Oracle MERGE adapter: `.../infrastructure/adapter/persistence/OracleCustomerUpsertPortAdapter.java`
- Import audit JDBC: `.../infrastructure/adapter/persistence/JdbcImportAuditPortAdapter.java`
- Async launcher + JDBC + domain policy wiring: `.../infrastructure/config/`

### 7.5 Why two classes named `*Config` under batch?

- **`CustomerImportJobConfig`**: builds the **`Job`** and fault-tolerant **`Step`** (chunk, retry/skip, job + skip/process audit listener registration).
- **`CustomerCsvItemReaderConfig`**: builds the **`@StepScope` `FlatFileItemReader`** so each run can resolve a different `inputFile` job parameter. Kept separate from the job graph for clarity and Spring Batch bean scopes. Resource resolution is shared with the API validator; for **`classpath:`** inputs it uses the application class loader so the file still resolves when the step runs on a **`batch-*`** thread (Rabbit → async `JobLauncher`). If you see **`Input resource must exist`** on the reader, confirm the command contains a bundled `classpath:` file or a staged `classpath:customer-imports/...` file under `target/classes/customer-imports/`.

### 7.6 Phase 3 — What to watch at each step (dev + RabbitMQ)

| Step | Where to look | What you should see |
|------|----------------|---------------------|
| 1. POST accepted | HTTP **202** body | `status=QUEUED`, new `correlationId` |
| 2. Message on broker | RabbitMQ UI → Queues → `customer.import.queue` | Message rate / ready count briefly > 0 |
| 3. Listener ran | App logs | `Received customer import command` then `Launched customer import job … jobExecutionId=` |
| 4. Mapping persisted | Oracle `IMPORT_LAUNCH_CORRELATION` or `GET …/by-correlation/…/job` | Row with your `correlation_id`, or **200** with `jobExecutionId` |
| 5. Batch progress | `GET …/status` | `STARTED` → `COMPLETED` (or `FAILED` + HTTP 500) |

### 7.7 RabbitMQ features used in this repo (and how to verify)

| Feature | How we use it | How to check |
|---------|----------------|--------------|
| **Durable queues + persistent messages** | Survive broker restart (defaults via Spring AMQP for declared queues) | UI: queue features “Durable”; restart broker and resend |
| **Direct exchange + routing key** | `customer.import.commands` → `customer.import.command` | UI: Exchanges / Bindings |
| **Dead-letter exchange (DLX)** | Failed messages after retry land in `customer.import.dlq` | UI: DLQ message count; consume/reject a poison message in a test env |
| **Prefetch (QoS)** | `prefetch=1` on the listener container | Steady one-in-flight consumer; UI consumer ack rate |
| **Listener retry + backoff** | Stateless retry on listener failures (`RejectAndDontRequeueRecoverer`) | App logs show retries; then DLQ growth if still failing |
| **JSON payloads** | `Jackson2JsonMessageConverter` on template + listener factory. For external local files, `inputFile` is the staged `classpath:customer-imports/...` value. | UI: Get messages → body is JSON `{correlationId,inputFile,schemaVersion}` |
| **Management plugin** | Ops visibility | `http://localhost:15672` |

Listener **ack mode** is **AUTO** (ack after successful handler return). Message durability is still provided by broker persistence + consumer retry/DLQ.

### 7.8 Scenario playbooks (flows to try)

1. **Happy path (`dev`)** — Start Oracle + Rabbit + app (`dev`). POST import with `classpath:customers.csv`. Poll `by-correlation` until `jobExecutionId`, then status until `COMPLETED`. Verify `CUSTOMER` rows in Oracle.
2. **Happy path (`audit-it`)** — No Rabbit. POST returns `STARTED` + `jobExecutionId` immediately; poll status (same as Phase 1–2 UX).
3. **External local file (`dev`)** — POST with `file:/Users/shubham.s/customers-any.csv`; app logs should show `Staged customer import file ... classpath:customer-imports/...`; Rabbit payload and job parameter use the staged classpath location.
4. **Missing `inputFile`** — POST without query param → **400** `Missing input file`; no queue message.
5. **Invalid file path** — POST with `file:/path/to/your/customers.csv` before editing it to a real path → **400** `Invalid input file`; no queue message.
6. **Broker down (`dev`)** — Stop Rabbit; restart app (expect failure) **or** POST while app up but broker stopped → **503** `Import command publish failed` if publish throws.
6. **Unknown correlation** — `GET …/by-correlation/{random-uuid}/job` before POST or for never-registered UUID → **404** once polling (or immediately if no row).
7. **Invalid correlation path** — `GET …/by-correlation/not-a-uuid/job` → **400** `Invalid correlation id`.
8. **Poison message (advanced)** — Publish an invalid JSON body manually to the queue (management “Publish message”) → consumer fails → after retries message moves to **`customer.import.dlq`**; inspect payload in UI.
9. **DLQ inspection** — UI → Queues → `customer.import.dlq` → get messages; requeue or discard only after you understand the failure.

### 7.9 Automated AMQP test

`CustomerImportAmqpEndToEndIntegrationTest` (`@ActiveProfiles("amqp-it")`) uses **Testcontainers** RabbitMQ + H2 batch metadata. Requires Docker when running `./mvnw test` (otherwise the test is skipped).

## 8) Tests

Tests are organized under `src/test/java/` with two top-level directories:

```
src/test/java/
├── unit/          ← fast, isolated (JUnit 5 + Mockito, no Spring context)
│   └── com/example/spring_batch_demo/
│       ├── SpringBatchDemoApplicationMainTest.java
│       ├── application/customer/
│       ├── application/customer/dto/
│       ├── domain/customer/
│       ├── infrastructure/adapter/batch/
│       ├── infrastructure/adapter/persistence/
│       ├── infrastructure/batch/config/
│       ├── infrastructure/config/
│       ├── infrastructure/diagnostics/
│       └── presentation/api/BatchJobControllerTest.java
└── integration/   ← Spring-backed (@SpringBootTest, @WebMvcTest)
    └── com/example/spring_batch_demo/
        ├── SpringBatchDemoApplicationTests.java
        ├── batch/
        │   └── CustomerImportBatchAuditIntegrationTest.java   (@ActiveProfiles("audit-it") + H2)
        ├── messaging/
        │   └── CustomerImportAmqpEndToEndIntegrationTest.java   (@ActiveProfiles("amqp-it") + Testcontainers RabbitMQ)
        └── presentation/api/
            ├── BatchJobControllerWebMvcIntegrationTest.java
            └── BatchJobImportInputFileApiIntegrationTest.java
```

### 8.1 Running tests

```bash
./mvnw clean test          # all tests
./mvnw clean install       # compile + test + package
./mvnw clean install -U    # same, force-update snapshots
```

### 8.2 Test database

Tests use **H2 in-memory** (Oracle-compat mode) — no running Oracle instance required.

Configuration: `src/main/resources/application-test.properties` (active for Spring Boot profile `test`)

### 8.3 Mockito mock maker

The project forces the **subclass** mock maker via `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` (content: `mock-maker-subclass`). This avoids Byte Buddy `IllegalArgumentException` on JVMs newer than what the current Byte Buddy release supports. Trade-off: `mockStatic` is unavailable with the subclass mock maker.

### 8.4 Code coverage

JaCoCo is configured in `pom.xml`. After `mvn test`, reports are generated at `target/site/jacoco/index.html`.

## 9) Architecture notes (Onion / ports & adapters)

This codebase is being evolved toward **Onion Architecture** (dependencies pointing inward):

- **Domain**: pure business rules (e.g. customer validation policies)
- **Application**: orchestration/use-cases and ports (interfaces)
- **Infrastructure**: Spring Batch + JDBC + file/CSV adapters (framework details)
- **Presentation**: REST controller and API DTOs

See `SD-ARCHITECTURE.md` for the target package structure and refactor plan, while keeping the current root package `com.example.spring_batch_demo`.

## 10) Troubleshooting

### `dev` profile fails at startup with RabbitMQ / connection refused

- Start RabbitMQ first: `docker compose -f docker-compose.rabbitmq.yml up -d` and wait for the container to be healthy.
- Or run **`audit-it`** / default **`test`** profile paths where messaging is **disabled** (no broker).

### "200 OK but no rows inserted"
- Check the API response: it returns **500** on job failure (with reason).
- Check app logs for `JOB FINISHED ... status=...`.

### "No CUSTOMER table"
- Make sure you are connected to **service `XEPDB1`** (PDB) as `batch_user`.
- If you query as SYS/SYSTEM, `user_tables` shows tables for SYS/SYSTEM only.

### Duplicate key errors (ORA-00001)
- This project uses `MERGE` now, so reruns should not fail on duplicate IDs.

### Job fails to start: `NoUniqueBeanDefinitionException` for `Job`
- Symptom: Spring cannot autowire the `Job` bean because multiple `Job` beans exist and no qualifier narrows the match.
- Cause: Lombok's `@RequiredArgsConstructor` does **not** propagate `@Qualifier` annotations from fields to constructor parameters. So `@Qualifier("customerJob")` on a field is silently ignored in the generated constructor.
- Fix: Replace `@RequiredArgsConstructor` with an explicit constructor and put `@Qualifier("customerJob")` on the constructor parameter.

### Controller returns 200 OK for a FAILED job (regression check)
- **Current code**: `GET .../status` returns **500** when `status` is **`FAILED`** (same JSON body), and **200** for other terminal states.
- If you ever see **200** for `FAILED` again, a regression reintroduced filtering on `failures` instead of **status alone** — a job can fail with an **empty** `failures` list.

### `mvn clean install` fails with Byte Buddy / Mockito errors
- Ensure you are running with **Java 21** (`java -version`). Java 25+ triggers Byte Buddy incompatibilities.
- The subclass mock maker (`src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`) should already be in place.
- If using Java 25+, Mockito `5.23.0` with `mock-maker-subclass` is required (already configured in `pom.xml`).

### Startup logs: `BeanPostProcessorChecker` / “not eligible for getting processed by all BeanPostProcessors”

- You may see **WARN** lines mentioning `JobRegistryBeanPostProcessor`, `dataSource`, or `transactionManager` during context refresh when **Spring Batch auto-configuration** registers batch infrastructure **early** relative to datasource beans.
- On Spring Boot **3.2.x** with the default Batch + JDBC stack, these messages are usually **benign** if the application still reaches **`Started SpringBatchDemoApplication`** and jobs run normally.
- If startup **fails**, treat the **first ERROR** or `APPLICATION FAILED TO START` block as the real cause (port in use, bad JDBC URL, missing driver), not these warnings alone.
- Optional: upgrade Spring Boot / Spring Batch when newer releases tighten initialization order; see Spring Boot issue tracker for “Batch” + “BeanPostProcessor” if you need upstream context.
