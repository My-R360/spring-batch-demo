# PROMPTS / CHANGE LOG

This file tracks what was requested in chat and what was changed in the project, in chronological order.

## How to maintain this file

- Add a new entry for each meaningful request/change.
- Keep entries chronological.
- For each entry, capture:
  - prompt/request summary
  - files/components changed
  - outcome/notes

---

## Timeline

### 01) Initial Spring Batch + Oracle review
- **Prompt summary**: Review existing Spring Batch CSV-to-DB code, suggest/fix corrections, and provide Oracle Docker setup/run steps.
- **Changes done**:
  - Corrected invalid Spring Batch imports/packages.
  - Fixed class/file placement and naming issues.
  - Added Oracle datasource configuration and JDBC driver setup.
  - Added schema initialization and detailed run steps.
- **Outcome**: Project became buildable/runnable with Oracle and manual run instructions.

### 02) Add REST trigger + configurable CSV input
- **Prompt summary**: Add endpoint trigger and support different input files.
- **Changes done**:
  - Added REST API endpoint to trigger import.
  - Added `inputFile` request parameter support.
  - Reader supports `classpath:` and `file:` resource locations.
  - Switched startup behavior so job runs on API trigger.
- **Outcome**: Import can be executed via Postman/curl with selectable file.

### 03) Build fixes (`mvn clean install` errors)
- **Prompt summary**: Fix Maven build issues.
- **Changes done**:
  - Replaced invalid dependencies with valid Spring Boot/Spring Batch artifacts.
  - Fixed compile issues caused by Lombok-generated methods/constructors not available.
  - Stabilized project build.
- **Outcome**: `./mvnw clean install` succeeded.

### 04) Dev profile safety for restarts
- **Prompt summary**: Make dev configuration restart-safe.
- **Changes done**:
  - Added/updated `application-dev.properties`.
  - Adjusted schema init behavior for development.
  - Added safer defaults in main profile.
- **Outcome**: Better local restart behavior.

### 05) Diagnostics/logging for troubleshooting
- **Prompt summary**: Explain full flow and add logs to detect failures.
- **Changes done**:
  - Added startup diagnostics for DB/user/table visibility.
  - Improved controller/job logging and failure reporting.
  - Improved response behavior for failed job executions.
- **Outcome**: Clear visibility into where failures occur.

### 06) Duplicate key failure fix
- **Prompt summary**: Fix ORA-00001 duplicate key on reruns.
- **Changes done**:
  - Replaced insert-only behavior with Oracle `MERGE` upsert behavior.
- **Outcome**: Re-running imports no longer fails for existing IDs.

### 07) GitHub repository setup
- **Prompt summary**: Create private repo under org and push code.
- **Changes done**:
  - Initialized git, configured remote, committed and pushed to private org repo.
  - Added `.gitignore` updates for local/non-project artifacts.
- **Outcome**: Project published privately in GitHub org.

### 08) Documentation expansion
- **Prompt summary**: Add setup/run docs and operational guidance.
- **Changes done**:
  - Added `README.md` and `RUNBOOK.md`.
  - Added architecture/design docs:
    - `SD-DESIGN.md`
    - `SD-ARCHITECTURE.md`
- **Outcome**: Project now has user/dev/operator documentation.

### 09) Onion architecture planning and refactor
- **Prompt summary**: Plan first, then refactor to onion architecture with SOLID/patterns.
- **Changes done**:
  - Introduced layers while keeping root package:
    - `domain`
    - `application`
    - `infrastructure`
    - `presentation`
  - Moved/rewired controller, batch use-case, reader/processor/writer adapters, configs, diagnostics.
  - Removed obsolete/unused classes and empty folders.
- **Outcome**: Layered architecture now implemented and build verified.

### 10) Add comments + Lombok support
- **Prompt summary**: Add method/interface comments and Lombok where appropriate.
- **Changes done**:
  - Enabled Lombok annotation processing in Maven build config.
  - Applied Lombok selectively (`@Data`, `@RequiredArgsConstructor`, `@Slf4j`) where useful.
  - Added Javadocs/comments for key interfaces/methods and configuration points.
- **Outcome**: Cleaner code and better readability/documentation.

### 11) Introduce application persistence port
- **Prompt summary**: Improve SOLID by adding an application port for persistence.
- **Changes done**:
  - Added `CustomerUpsertPort` in application layer.
  - Implemented Oracle adapter in infrastructure.
  - Batch writer now delegates through the application port.
- **Outcome**: Better dependency inversion and testability.

### 12) Add external architecture references
- **Prompt summary**: Read onion architecture resources and add references.
- **Changes done**:
  - Read supplied links.
  - Added “Further reading” section in architecture doc with takeaways.
- **Outcome**: Architecture docs now include reference context.

### 13) Current request: add this prompts file
- **Prompt summary**: Create a prompts file that tracks all requests/changes chronologically and keep documenting future changes.
- **Changes done**:
  - Added this `PROMPTS.md` file.
  - Backfilled major historical requests and project changes.
  - Added maintenance guidance at top.
- **Outcome**: Prompt-to-change traceability is now documented in-repo.

### 14) Add Cursor project rules and skill
- **Prompt summary**: Create reusable Cursor rules and skills tailored to this project for future development.
- **Changes done**:
  - Added project rules under `.cursor/rules/`:
    - `onion-architecture.mdc`
    - `spring-batch-conventions.mdc`
    - `docs-and-prompt-log.mdc`
  - Added project skill under `.cursor/skills/`:
    - `spring-batch-onion-workflow/SKILL.md`
- **Outcome**: Future Cursor sessions can auto-apply project-specific architecture and workflow guidance.

### 15) Reorganize tests into unit/integration folders
- **Prompt summary**: Rearrange test files into separate folders for unit tests and integration tests; verify build/run.
- **Changes done**:
  - Moved tests under:
    - `src/test/java/unit/...`
    - `src/test/java/integration/...`
  - Kept package names unchanged so code references and test discovery still work.
  - Re-ran build and startup smoke checks.
- **Outcome**: Clearer test organization with successful build verification.

### 16) Fix `mvn clean install -U` failures on Java 25
- **Prompt summary**: Build failing with Mockito/Byte Buddy errors; fix and verify.
- **Changes done**:
  - Investigated runtime evidence showing `mvn` used Java 25.
  - Added test resource override:
    - `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
    - content: `mock-maker-subclass`
  - Updated `SpringBatchDemoApplicationMainTest` to avoid static Mockito mocking (unsupported with subclass mock maker).
  - Added then removed temporary debug instrumentation after verification.
- **Outcome**: `mvn clean install -U` stabilized; test suite passes under Java 25 too.

### 17) Set Java 21 as global default
- **Prompt summary**: Configure system so Java 21 is used by default without manual export before each build.
- **Changes done**:
  - Updated `~/.bash_profile` (user shell config) to set:
    - `JAVA_HOME=$(/usr/libexec/java_home -v 21)`
    - `PATH` preferring Java 21 binary path.
  - Verified `java -version` and `mvn -v` report Java 21 in fresh bash login shell.
- **Outcome**: New Bash sessions default to Java 21 automatically.

### 18) Documentation alignment refresh
- **Prompt summary**: Check if markdown docs are fully updated and refresh stale ones.
- **Changes done**:
  - Updated `SD-DESIGN.md` with current onion-layer classes and actual file paths.
  - Updated `AGENTS.md` gotchas to reflect H2-backed test profile and 8080 port caveat.
  - Replaced stale Spring Initializr boilerplate in `HELP.md` with project-specific quick help.
  - Added these recent updates to this prompt log.
- **Outcome**: Markdown docs align with current implementation and recent fixes.

---

## Roadmap (ordered phases — implement in this sequence)

Each phase builds on the previous one. The system stays deployable and working after every phase.

---

### Phase 1 — Async Import API + Batch-Level Retry _(foundational)_

**Goal**: Decouple the HTTP caller from job execution so long-running imports don't block the request. Add proper retry/resilience at the batch step level so transient failures (DB hiccups, connection drops) don't kill the whole job.

**What changes (async)**:
- `POST /api/batch/customer/import` returns **202 Accepted** immediately with a `jobExecutionId`.
- New endpoint: `GET /api/batch/customer/import/{jobExecutionId}/status` returns progress/status/failures.
- `JobLauncher` switches to **async mode** via a `TaskExecutor` bean.
- `CustomerImportUseCase` interface splits into `launchImport(...)` (returns ID) and `getImportStatus(id)`.
- `CustomerImportResult` gains a `progress` field (read/write/skip counts from `StepExecution`).

**What changes (batch-level retry)**:
- Configure Spring Batch **fault tolerance** on `customerStep`:
  - `faultTolerant()` with `.retry(TransientDataAccessException.class)` and `.retryLimit(3)`.
  - `.skip(FlatFileParseException.class)` with `.skipLimit(100)` for malformed CSV rows.
  - `.retryPolicy()` with **exponential backoff** via a custom `BackOffPolicy` (initial 1s, multiplier 2x, max 8s, jitter).
- This means: if a chunk write to Oracle fails due to a transient issue, Spring Batch retries that chunk up to 3 times with backoff before marking the step failed. Bad CSV rows are skipped (not retried) and counted.
- `JobCompletionListener` logs retry/skip counts from `StepExecution` metadata.

**Layer impact**:
| Layer | Change |
|---|---|
| Presentation | Controller returns 202; new status endpoint |
| Application | Use-case interface gains status query method |
| Infrastructure | Async `TaskExecutor` config; status lookup from `JobExplorer`; step gains `faultTolerant()` retry + skip + backoff |
| Domain | None |

**Why first**: Every later phase (reporting, RabbitMQ) benefits from the job already being async and self-healing. Smallest change with highest leverage.

**Docs to update**: `README.md`, `RUNBOOK.md` (new endpoint + curl, retry behavior), `SD-DESIGN.md`, `PROMPTS.md`.

---

### Phase 2 — Reporting & Audit Pipeline

**Goal**: Know exactly what happened in each import — rows read, accepted, rejected (with reasons), written.

**What changes**:
- Add a **`SkipListener`** (infrastructure) to capture skipped/rejected rows and why.
- Introduce a **domain audit model**: `ImportAuditReport` (counts + list of `RejectedRow` with reason).
- Add an **application port** `ImportAuditPort` for persisting audit data.
- Infrastructure adapter writes audit rows to a new `IMPORT_AUDIT` / `IMPORT_REJECTED_ROW` table (Oracle MERGE-safe).
- Extend the status endpoint from Phase 1 to include the audit report.
- Optionally expose `GET /api/batch/customer/import/{id}/report` for a detailed breakdown.

**Layer impact**:
| Layer | Change |
|---|---|
| Presentation | Status/report endpoint extended |
| Application | New port `ImportAuditPort`; result model extended |
| Infrastructure | `SkipListener`, audit DB adapter, schema additions |
| Domain | `ImportAuditReport`, `RejectedRow` value objects |

**Why second**: With async in place, imports run in the background. Reporting gives visibility into what those background jobs actually did. Required before RabbitMQ because message-driven jobs have no human watching — audit is your observability.

**Docs to update**: `RUNBOOK.md` (new tables, verification queries), `SD-DESIGN.md` (observer/audit pattern), `SD-ARCHITECTURE.md` (new port), `PROMPTS.md`.

---

### Phase 3 — RabbitMQ + Message-Level Retry & Resilience

**Goal**: Put RabbitMQ between callers and the batch service so the system never gets overwhelmed by too many large requests. If the service goes down, messages persist in the queue and get processed when it comes back.

#### Flow A — User-triggered (implementing now)

The user hits the REST API. Instead of launching the job directly, the controller **publishes a message** to RabbitMQ and returns 202 immediately. The listener picks it up at a controlled rate.

```
User / Postman
    │
    POST /api/batch/customer/import
    │
    ▼
Controller (presentation)
    │
    publishes CustomerImportCommand to RabbitMQ
    returns 202 + correlationId
    │
    ▼
RabbitMQ ── queue: customer.import ── (durable, persisted on disk)
    │
    ▼
MessageListener (infrastructure/messaging)
    │
    prefetch=1, manual ack
    retry with exponential backoff + jitter
    max 3 attempts → DLQ
    │
    calls CustomerImportUseCase.launchImport(...)
    │
    ▼
Spring Batch Job → chunks (with batch-level retry from Phase 1) → Oracle
    │
    ack on success → message removed
    nack on failure → retry / DLQ after retries exhausted
```

The status endpoint from Phase 1 (`GET .../status`) still works — the user polls it with the correlationId/jobExecutionId.

#### Flow B — Bank event-driven (deferred, not implementing now)

For production, the bank or upstream system publishes events directly to the same RabbitMQ exchange. No HTTP trigger involved — pure event-driven. Same queue, same listener, same use-case. The only difference is _who publishes_. This will be designed when we integrate with the actual bank event pipeline.

```
Bank / Upstream System ──publish──▶ RabbitMQ Exchange ──▶ same queue ──▶ same listener
```

_Implementation deferred until bank integration requirements are concrete._

#### What changes

1. **Dependency**: add `spring-boot-starter-amqp` to `pom.xml`.
2. **RabbitMQ infra** (Docker): `rabbitmq:3-management` on ports 5672 (AMQP) / 15672 (management UI).
3. **Message model** (application layer): `CustomerImportCommand` — contains `inputFile` resource path, correlationId, requester metadata.
4. **Controller change** (presentation): instead of calling use-case directly, publishes `CustomerImportCommand` to RabbitMQ, returns 202.
5. **Listener** (infrastructure/messaging): `CustomerImportMessageListener` — `@RabbitListener` that deserializes, calls `CustomerImportUseCase.launchImport(...)`, and manually acks/nacks.
6. **RabbitMQ config** (infrastructure/config): `RabbitMqConfig` — exchange `batch.events`, queue `customer.import`, DLQ `customer.import.dlq`, routing key, JSON message converter.
7. **Publisher** (infrastructure/messaging): `CustomerImportEventPublisher` — called by the controller to publish commands to the exchange.

#### Message-level retry & resilience stack

| Mechanism | Purpose | Config |
|---|---|---|
| **Prefetch = 1** | Listener grabs only 1 message at a time. 50 simultaneous requests = 50 queued messages, processed one by one. Service never overwhelmed. | `spring.rabbitmq.listener.simple.prefetch=1` |
| **Manual ack** | Message removed from queue only after job completes successfully. Service crash mid-job = message redelivered automatically. | `spring.rabbitmq.listener.simple.acknowledge-mode=manual` |
| **Exponential backoff** | Failed message retried after 1s → 2s → 4s (not immediately). Prevents hammering a failing dependency (e.g., Oracle down). | Spring Retry `ExponentialBackOffPolicy` (initial=1000ms, multiplier=2.0, max=10000ms) |
| **Jitter** | Adds randomness to backoff. Prevents thundering herd when multiple messages fail and retry at the same time. | `ExponentialRandomBackOffPolicy` |
| **Max retries = 3** | After 3 attempts, stop retrying and route to DLQ. | `RetryTemplate` maxAttempts=3 |
| **Dead-letter queue** | Messages that exhaust retries land in `customer.import.dlq`. Ops inspects, fixes data, replays. Nothing lost. | Exchange/queue DLX/DLK args |
| **Durable queue + persistent messages** | Queue and messages survive RabbitMQ restart. | `durable=true`, `deliveryMode=PERSISTENT` (Spring AMQP defaults) |
| **Service down resilience** | If the batch service goes down entirely, messages sit in RabbitMQ on disk. When service comes back, listener auto-reconnects and drains the queue. | Built into AMQP protocol + Spring auto-reconnect |

#### How retry layers stack (batch-level from Phase 1 + message-level from Phase 3)

```
Message arrives from queue
    │
    ▼
Listener calls launchImport(...)
    │
    ▼
Spring Batch step executes chunks
    │
    chunk write fails (transient DB error)
    │
    ▼
Batch-level retry (Phase 1): retry chunk up to 3× with backoff
    │
    still failing after 3 chunk retries → step fails → job fails
    │
    ▼
Message-level retry (Phase 3): nack → backoff → redeliver message
    │
    still failing after 3 message retries → DLQ
    │
    ▼
Ops reviews DLQ, fixes root cause, replays message
```

Two layers: inner (chunk granularity, fast retries for transient blips) and outer (job granularity, slower retries for sustained failures). They don't conflict — batch-level retry handles momentary hiccups; message-level retry handles cases where the whole job fails.

#### Layer impact

| Layer | Change |
|---|---|
| Presentation | Controller publishes to RabbitMQ instead of calling use-case directly; still returns 202 |
| Application | `CustomerImportCommand` model; use-case interface unchanged |
| Infrastructure | New `infrastructure/messaging/` package: listener, publisher, config, retry policy |
| Domain | None |

#### What this helps with

- **No service overload**: Prefetch=1 means the service processes at its own pace regardless of how many requests arrive.
- **Crash resilience**: Unprocessed messages survive service restarts. Unacked messages get redelivered.
- **Retry without data loss**: Exponential backoff + jitter + DLQ = every failure is retried intelligently, and nothing is silently dropped.
- **Decoupling**: Controller doesn't wait for the job. Publisher and consumer are independent. Scales horizontally with competing consumers later.
- **Ops visibility**: RabbitMQ management UI (port 15672) shows queue depth, message rates, DLQ contents.

#### Infrastructure additions

- RabbitMQ server (Docker: `rabbitmq:3-management` on ports 5672/15672)
- `application.properties` gains `spring.rabbitmq.*` connection config
- `application-dev.properties` gains local RabbitMQ defaults
- `RUNBOOK.md` gains RabbitMQ Docker setup, management UI access, DLQ inspection/drain steps

**Docs to update**: `README.md` (prerequisites), `RUNBOOK.md` (RabbitMQ Docker setup, management UI, DLQ ops), `SD-ARCHITECTURE.md` (messaging adapter layer), `SD-DESIGN.md` (observer + retry patterns), `PROMPTS.md`.

---

### Phase 4 — Design-First OpenAPI Workflow

**Goal**: Make the REST API contract-first — define endpoints in an OpenAPI spec, generate DTOs and controller interfaces, implement against generated code.

**What changes**:
- Add `openapi.yaml` as the single source of truth for all REST endpoints (import trigger, status, report).
- Add `openapi-generator-maven-plugin` to `pom.xml` to generate Java interfaces + DTOs at build time.
- Controller implements the generated interface (compile-time contract enforcement).
- Keep everything in the same repo (plugin + spec, not a separate service).

**Layer impact**:
| Layer | Change |
|---|---|
| Presentation | Controller implements generated interface; hand-written DTOs replaced by generated ones |
| Application | Result/command models may align with generated DTOs or map to them |
| Infrastructure | None |
| Domain | None |

**Why fourth**: By this point, the API surface is stable (async trigger, status, report, RabbitMQ-triggered). Locking down the contract now prevents drift and makes the API consumable by other teams with generated clients.

**Docs to update**: `README.md` (build instructions), `SD-DESIGN.md` (contract-first pattern), `PROMPTS.md`.

---

### Phase 5 — Housekeeping & Hardening _(ongoing, in parallel where safe)_

These can be done alongside any phase:

- [ ] **`CustomerSourcePort`**: Abstract CSV reading behind an application port (mentioned in `SD-ARCHITECTURE.md` as optional). Enables swapping CSV for API/S3/SFTP sources without touching application/domain layers.
- [ ] **Integration test coverage**: End-to-end batch flow test with H2 (JaCoCo is already wired; target the full reader→processor→writer path).
- [ ] **CI pipeline**: GitHub Actions for `mvn clean verify` on PRs; JaCoCo report upload; branch protection.
- [ ] **Cursor rules & skills**: Keep `.cursor/rules/` and `.cursor/skills/` updated as new patterns are introduced (messaging conventions, OpenAPI conventions).
- [ ] **PROMPTS.md discipline**: Append an entry after every meaningful change (already the convention).

---

### Phase dependency graph

```
Phase 1 (Async API)
    │
    ├──▶ Phase 2 (Reporting/Audit)
    │        │
    │        └──▶ Phase 3 (RabbitMQ)
    │                 │
    │                 └──▶ Phase 4 (OpenAPI)
    │
    └──▶ Phase 5 (Housekeeping) ← can run in parallel at any point
```

