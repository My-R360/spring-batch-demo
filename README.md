# Spring Batch CSV → Oracle (Docker XE)

This project imports customers from a CSV file into an Oracle database using **Spring Batch**. A REST API triggers the import so you can run it from **Postman**.

## What it does

- Reads CSV rows into `Customer` objects
- Filters invalid rows (email must contain `@`)
- Uppercases customer names
- Writes to Oracle table `CUSTOMER` using an **upsert** (`MERGE`) so reruns do not fail on duplicate IDs
- **Async API**: POST returns 202 immediately with a `jobExecutionId`; poll status via GET
- **Fault-tolerant batch step**: retries transient DB errors (3x, exponential backoff), skips malformed CSV rows

## Prerequisites

- **Java 21** (exactly — set `JAVA_HOME` to Java 21; see RUNBOOK § 1)
- Docker (for Oracle XE)
- Maven Wrapper included (`./mvnw`)

## Quick start

### 1) Start Oracle in Docker

```bash
docker ps | grep oracle-db || true
```

If you don't have it running yet, see `RUNBOOK.md`.

### 2) Run the app (dev profile recommended)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile:
- auto-creates `CUSTOMER` (idempotent)
- auto-creates Spring Batch metadata tables (`BATCH_*`)
- enables extra JDBC/batch logs

### 3) Trigger import (Postman/curl)

The POST endpoint launches the job **asynchronously** and returns **202 Accepted** immediately. Query parameter **`inputFile` is required** (non-blank Spring resource location); omitting it or sending only whitespace returns **400** with a `ProblemDetail` body.

Bundled sample CSV on the classpath:

```bash
curl -X POST "http://localhost:8080/api/batch/customer/import?inputFile=classpath:customers.csv"
# → 202  {"jobExecutionId": 1}
```

Another classpath CSV:

```bash
curl -X POST "http://localhost:8080/api/batch/customer/import?inputFile=classpath:customers-01.csv"
```

Import a file from your machine:

```bash
curl -X POST "http://localhost:8080/api/batch/customer/import?inputFile=file:/Users/shubham.s/customers.csv"
```

### 4) Poll job status

Use the `jobExecutionId` from the POST response:

```bash
curl "http://localhost:8080/api/batch/customer/import/1/status"
# → 200  {"jobExecutionId":1,"status":"COMPLETED","failures":[],"readCount":6,"writeCount":5,"skipCount":0,"filterCount":0,"rejectedSample":[]}
```

While the job is running, `status` will be `STARTED`. When done, it will be `COMPLETED` or `FAILED`.

## Project structure (high level)

- **Presentation (API)**:
  - `.../presentation/api/BatchJobController.java`
  - `.../presentation/api/exceptions/BatchJobApiExceptionHandler.java` (scoped errors + `ProblemDetail`)
- **Application (ports + DTOs)**:
  - `.../application/customer/port/CustomerImportUseCase.java`
  - `.../application/customer/port/CustomerUpsertPort.java`
  - `.../application/customer/port/ImportAuditPort.java`
  - `.../application/customer/dto/CustomerImportResult.java`, `ImportAuditReport.java` (job / polling + audit, not domain)
  - `.../application/customer/CustomerImportInputFile.java` (required `inputFile` path rules); `.../application/customer/exceptions/` (`ImportJobLaunchException`, `MissingInputFileException`)
- **Domain (model + policy)**:
  - `.../domain/customer/Customer.java`
  - `.../domain/importaudit/` (`RejectedRow`, `ImportRejectionCategory`)
  - `.../domain/customer/policy/CustomerImportPolicy.java`, `.../domain/customer/policy/EmailAndNameCustomerImportPolicy.java`
  - `.../domain/validation/package-info.java` (placeholder for cross-cutting validation)
- **Common (JDK-only helpers)**:
  - `.../common/package-info.java`
- **Infrastructure**:
  - **Batch wiring (`@Configuration`)**: `.../infrastructure/batch/config/CustomerImportJobConfig.java` (job + fault-tolerant step), `CustomerCsvItemReaderConfig.java`, `CustomerImportAuditListenerConfig.java`
  - **Adapters (implementations)**: `.../infrastructure/adapter/batch/` — use-case impl, processor/writer adapters, `JobCompletionListener`, `CustomerImportAuditStepListener`; `.../infrastructure/adapter/persistence/OracleCustomerUpsertPortAdapter.java` (Oracle MERGE), `JdbcImportAuditPortAdapter.java`
  - **Other config**: `.../infrastructure/config/AsyncJobLauncherConfig.java`, `JdbcConfig`, `DomainPolicyConfig`
  - **Dev DB diagnostics**: `.../infrastructure/diagnostics/DevStartupDiagnostics.java`
- **Schema init**: `src/main/resources/schema.sql`
- **Sample CSVs**: `customers.csv`, `customers-01.csv` … `customers-04.csv`, `customers-phase2-audit-sample.csv`, `customers-import-audit-sample.csv` (integration / audit demos) under `src/main/resources/`

## Tests

Tests live under `src/test/java/` and are split into two root folders:

| Folder | Purpose | Runner |
|--------|---------|--------|
| `unit/` | Fast, isolated tests using JUnit 5 + Mockito (no Spring context) | Plain JUnit |
| `integration/` | Tests that boot (part of) the Spring context (`@SpringBootTest`, `@WebMvcTest`) backed by an in-memory H2 database | Spring Test |

Run everything:

```bash
./mvnw clean test
```

H2 + audit smoke without Oracle Docker: `./mvnw spring-boot:run -Dspring-boot.run.profiles=audit-it` (see `RUNBOOK.md` §4.3).

Key test-infrastructure files:

- `src/main/resources/application-test.properties` — H2 in-memory datasource (Oracle-compat mode) when profile `test` is active; auto-init off
- `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` — forces **subclass** mock maker (avoids Byte Buddy issues on newer JVMs)

> **No live Oracle is needed to run tests.** Integration tests use H2 with `MODE=Oracle`.

## Batch flow (conceptual)

HTTP POST → 202 Accepted (async) → Presentation Controller → Application UseCase → Infrastructure (Async JobLauncher) → Job → Fault-Tolerant Step (retry + skip) → Reader → Processor → Writer → Oracle.

Poll: GET status → Application UseCase → JobExplorer → progress counts + `filterCount` + `rejectedSample`; GET report → paginated `IMPORT_REJECTED_ROW` audit.

## Architecture & design docs

- **Design patterns & SOLID**: `SD-DESIGN.md`
- **Architecture (Onion) & target structure**: `SD-ARCHITECTURE.md`

See `RUNBOOK.md` for a detailed, step-by-step explanation and troubleshooting tips.

## Slide deck (Slidev)

Optional **Slidev** deck (Mermaid, async + fault-tolerance paths): keep a local `slidev/` directory (that path is **gitignored**—not pushed). See `slidev/README.md` for `npm install` / `npm run dev` when you have a copy.
