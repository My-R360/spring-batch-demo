# Roadmap & future work

Single place for **what we planned**, **what is done**, and **what is left**—aligned with `PROMPTS.md`, `SD-ARCHITECTURE.md`, and the Slidev talk track (`slidev/slides.md`, “What to improve next”).

**Legend**

| Mark | Meaning |
|------|---------|
| ✅ | Done (matches current `main` / branch behavior) |
| ⬜ | Not started |
| 🔄 | Partially done / follow-up polish |

---

## Dependency order (high level)

```
Phase 1 (async API + batch fault tolerance) ✅
    ├──▶ Phase 2 (reporting & audit)
    │        └──▶ Phase 3 (RabbitMQ + message-level retry)
    │                 └──▶ Phase 4 (OpenAPI contract-first)
    └──▶ Phase 5 (housekeeping & hardening) ← can run in parallel when safe
```

---

## Foundation (before / alongside numbered phases)

| Item | Status | Notes |
|------|--------|--------|
| Onion layering (presentation → application → domain; infra owns Batch/JDBC) | ✅ | See `SD-ARCHITECTURE.md` |
| Domain `Customer` as Java `record`; immutable policy | ✅ | `PROMPTS.md` §19 |
| Application port `CustomerUpsertPort` + Oracle adapter | ✅ | |
| Docs: README, RUNBOOK, SD-DESIGN, SD-ARCHITECTURE | ✅ | Kept in sync with behavior |
| Cursor rules + `spring-batch-onion-workflow` skill | ✅ | `.cursor/rules/`, `.cursor/skills/` |
| Optional Slidev deck + build/narrative rules | ✅ | `slidev/slides.md`, `slidev-deck.mdc` |

---

## Phase 1 — Async import API + batch-level fault tolerance

**Goal:** HTTP does not block on job completion; step survives transient DB issues and malformed CSV lines.

| Item | Status | Notes |
|------|--------|--------|
| `POST /api/batch/customer/import?inputFile=…` → **202** + `{ jobExecutionId }` (400 if missing/blank) | ✅ | |
| `GET /api/batch/customer/import/{id}/status` | ✅ | 404 for unknown id |
| Async `JobLauncher` (`TaskExecutorJobLauncher` + executor) | ✅ | `AsyncJobLauncherConfig` |
| `CustomerImportUseCase`: `launchImport` + `getImportStatus` | ✅ | |
| `CustomerImportResult`: failures + `readCount` / `writeCount` / `skipCount` | ✅ | |
| Fault-tolerant `customerStep`: retry `TransientDataAccessException` (limit 3) | ✅ | |
| Exponential backoff on retry (1s → 2x → max 8s) | ✅ | |
| Skip `FlatFileParseException`, skip limit 100 | ✅ | |
| Failure messages for polling from persisted exit descriptions | ✅ | `JobExplorer`-safe |
| `JobCompletionListener` step summaries (read/write/skip/rollback/commit/filter) | ✅ | |
| Bean override / `@Qualifier` on `Job` + async launcher wiring | ✅ | Post–Phase 1 bug fixes |
| Controller: FAILED status → 500 without requiring non-empty failures | ✅ | |
| Tests + docs updated for Phase 1 | ✅ | `PROMPTS.md` §20–21 |

**Phase 1 polish (optional, not blocking Phase 2)**

| Item | Status | Notes |
|------|--------|--------|
| Externalize retry/skip/chunk/backoff via `@ConfigurationProperties` | ⬜ | Slidev “improve next” |
| Richer status payload (e.g. step name, duration, last change) without exposing Batch types in domain | ⬜ | Slidev “improve next” |
| Jitter on batch retry backoff (in addition to current exponential policy) | ⬜ | Mentioned in roadmap text vs current fixed backoff |

---

## Phase 2 — Reporting & audit pipeline

**Goal:** Per-import visibility—accepted vs rejected rows with reasons.

| Item | Status | Notes |
|------|--------|--------|
| `SkipListener` + `ItemProcessListener` to capture skips/rejects + reasons | ✅ | `CustomerImportAuditStepListener` |
| Domain audit model (`RejectedRow`, `ImportRejectionCategory`) + application `ImportAuditReport` | ✅ | `domain.importaudit`, `application.customer.dto` |
| Application port `ImportAuditPort` + `JdbcImportAuditPortAdapter` | ✅ | Oracle table `IMPORT_REJECTED_ROW` in `schema.sql` |
| Extend status + `GET .../report` with audit summary | ✅ | `filterCount`, `rejectedSample`, paginated report |
| RUNBOOK / SD-DESIGN / SD-ARCHITECTURE updates | ✅ | |

---

## Phase 3 — RabbitMQ + message-level retry & resilience

**Goal:** Throttle and persist work at the message boundary; DLQ for poison / exhausted retries.

| Item | Status | Notes |
|------|--------|--------|
| **Flow A (REST → publish):** controller publishes command, returns 202; listener calls `launchImport` | ⬜ | Primary implementation path |
| **Flow B (bank / upstream events):** deferred until requirements are concrete | ⬜ | Design-only for now |
| Docker RabbitMQ (`rabbitmq:3-management`), `spring-boot-starter-amqp` | ⬜ | |
| `CustomerImportCommand` (or equivalent) in application layer | ⬜ | |
| Exchange/queue/DLQ, JSON converter, manual ack, prefetch=1 | ⬜ | |
| Message-level retry + backoff + jitter; max attempts → DLQ | ⬜ | Stacks *outside* Phase 1 chunk retry |
| RUNBOOK (ops: UI, DLQ inspect/replay) | ⬜ | |

---

## Phase 4 — Design-first OpenAPI

**Goal:** Contract-first REST; generated interfaces/DTOs; less drift vs hand-written controllers.

| Item | Status | Notes |
|------|--------|--------|
| `openapi.yaml` as source of truth | ⬜ | |
| `openapi-generator-maven-plugin`; controller implements generated API | ⬜ | Prefer after Phase 2–3 stabilize surface |

---

## Phase 5 — Housekeeping & hardening (ongoing)

| Item | Status | Notes |
|------|--------|--------|
| `CustomerSourcePort` — abstract CSV behind a port (S3/SFTP/API later) | ⬜ | Listed in `PROMPTS.md` roadmap |
| Stronger integration tests (full reader → processor → writer on H2) | ⬜ | |
| CI: GitHub Actions, `mvn verify`, JaCoCo upload, branch protection | ⬜ | |
| Keep `.cursor/` rules/skills in sync with new patterns | 🔄 | Ongoing |
| `PROMPTS.md` entries after meaningful changes | 🔄 | Convention |

---

## Productive suggestions (cross-phase)

These are **not** all committed phases; they are high-value directions to consider when touching related areas.

1. **Configuration & 12-factor** — Batch tuning (chunk, retry, skip, executor pool) via `@ConfigurationProperties` and profile-specific YAML; avoids code changes for ops experiments (matches Slidev “externalize” item).
2. **Security** — If the API leaves demo-only use: authentication, authorization, rate limits, and optional IP allowlists (Slidev “improve next”).
3. **Observability** — Micrometer timers/counters for launches, completions, skips, DLQ depth (after Phase 3); structured logging fields (`jobExecutionId`, correlation id).
4. **API ergonomics** — Optional `Location` / `Retry-After` headers on 202; correlation id returned with `jobExecutionId` for tracing through logs and future queues.
5. **Schema lifecycle** — Flyway/Liquibase for `CUSTOMER` + batch metadata + `IMPORT_REJECTED_ROW`; reduces profile-only DDL drift.
6. **Idempotency** — Document or enforce job-parameter strategy for “same file re-run” vs accidental duplicate launches when multiple publishers exist (especially with Phase 3).
7. **Horizontal scale** — After RabbitMQ: competing consumers with clear job-instance isolation and DB migration strategy for embedded vs external `JobRepository` (only if multi-instance becomes a goal).

---

## How to keep this file honest

- After completing a phase, flip ⬜ → ✅ and add a pointer to the `PROMPTS.md` log entry.
- Prefer small, shippable slices within each phase so `main` stays deployable.

Last reviewed: **2026-04-19** (Phase 2 audit/reporting implemented; see `PROMPTS.md` §32).
