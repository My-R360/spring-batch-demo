# How to present this deck

## 10–15 minutes (lightning)

1. Title + value prop
2. Problem / non-goals
3. Onion architecture (one diagram)
4. Runtime components (one diagram)
5. Happy path sequence (trim speaker detail; show Result only)
6. Async state diagram (single slide)
7. Filters vs skips (one slide)
8. Closing: curl + Java 21 + link to RUNBOOK

**Skip or skim:** detailed failure trees (DB down, skip limit), full class diagram, testing deep-dive, “improve next” unless asked.

## 25–30 minutes (walkthrough)

Use the full `slides.md` order:

- All architecture slides
- Happy path + polling sequence
- State diagram + domain filter flow
- Malformed CSV + transient DB paths (with Result boxes)
- Worst-case / ops slide (table)
- Class diagram (domain + port + adapter)
- Testing strategy
- Closing + improvements

**Optional deep dive:** open IDE on `CustomerImportJobConfig.java` while on fault-tolerance slide; show `JOB_EXECUTION` query from RUNBOOK on ops slide.

## Presenter checklist (repo files)

Skim these before presenting:

| Topic | Path |
|--------|------|
| HTTP API | `src/main/java/.../presentation/api/BatchJobController.java` |
| Use-case port + result | `src/main/java/.../application/customer/port/CustomerImportUseCase.java`, `CustomerImportResult.java` |
| Batch orchestration | `src/main/java/.../infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java` |
| Job / step / fault tolerance | `src/main/java/.../infrastructure/config/batch/CustomerImportJobConfig.java` |
| Processor → domain | `src/main/java/.../infrastructure/adapter/batch/CustomerItemProcessorAdapter.java`, `domain/customer/policy/EmailAndNameCustomerImportPolicy.java` |
| Oracle upsert | `src/main/java/.../infrastructure/adapter/persistence/OracleCustomerUpsertPortAdapter.java` |
| Async launcher | `src/main/java/.../infrastructure/config/AsyncJobLauncherConfig.java` |
| Listener logs | `src/main/java/.../infrastructure/adapter/batch/JobCompletionListener.java` |
| Tests | `src/test/java/unit/...`, `src/test/java/integration/...` |

---

## Phase 2 deck (`deck-phase2.md`)

Use when the audience already knows Phase 1 (or after `slides.md` in a longer session).

**Suggested order (25–35 min):** Phase 1 vs 2 table → chunk hook ASCII → categories → **full HTTP flows** (POST 202/400/500, GET status 200/404/500, GET report + pagination) → request/response JSON slides → HTTP cheat sheet → types table → Mermaid deps + lifecycle → DDL table → listener + `REQUIRES_NEW` → profiles → curl chain → verify → roadmap.

**Presenter files:**

| Topic | Path |
|--------|------|
| Audit listener | `.../infrastructure/adapter/batch/CustomerImportAuditStepListener.java` |
| Audit port + JDBC | `.../application/customer/port/ImportAuditPort.java`, `.../JdbcImportAuditPortAdapter.java` |
| Step wiring (listeners) | `.../infrastructure/config/batch/CustomerImportJobConfig.java`, `CustomerImportAuditListenerConfig.java` |
| DTOs | `ImportAuditReport.java`, extended `CustomerImportResult.java` |
| Domain audit types | `domain/importaudit/` |
| H2 smoke profile | `application-audit-it.properties`, `NoOpCustomerUpsertPortAdapter.java` |

```bash
cd slidev && npm run dev:phase2
```
