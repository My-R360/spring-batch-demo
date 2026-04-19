# SD: Architecture (Onion) + Project Structure

This document describes the architecture used and the target layering for this project.

## Architecture used

### Runtime architecture

- **Spring Boot** app exposing a REST API (HTTP) that triggers a **Spring Batch** job.
- **Spring Batch** runs a chunk-oriented step: CSV read → validate/transform → write to Oracle.
- **Oracle XE** runs in Docker and stores:
  - your business table `CUSTOMER`
  - Spring Batch metadata tables (`BATCH_*`)

### Architectural style: Onion Architecture (target)

This project is being evolved toward **Onion Architecture** while keeping the root package:

- Root package: `com.example.spring_batch_demo`

Onion layering (inner → outer):

1. **Domain** (core business rules; no Spring/JDBC/Batch)
2. **Application** (use-cases; ports/interfaces; orchestrates domain)
3. **Infrastructure** (adapters: Spring Batch, JDBC, CSV, logging, diagnostics)
4. **Presentation** (REST controllers)

Dependency rule:

- Code may depend **only inward**, never outward (domain must not import Spring Batch classes).

**DTOs on use-case contracts:** Types such as `CustomerImportResult` live in **`application.customer.dto`** as the **return shape** of `CustomerImportUseCase` (under **`application.customer.port`**). Infrastructure adapters (e.g. `SpringBatchCustomerImportUseCase`) **construct** those records — still an inward dependency (infra → application API), not application “calling down” into infra.

They are **not** domain models: they carry **job / polling** concerns (`BatchStatus`, read/skip/write counters). **`Customer`** stays in **domain** as the business row shape.

## Current code mapping (as-is)

Today, the project is functional and has been refactored into onion-style layers. Key files:

- Presentation:
  - `presentation/api/BatchJobController.java` (POST → 202 async launch; GET → poll status)
  - `presentation/api/exceptions/BatchJobApiExceptionHandler.java` (scoped `ProblemDetail` for this API)
- Application:
  - `application/customer/port/CustomerImportUseCase.java`, `CustomerUpsertPort.java`
  - `application/customer/dto/CustomerImportResult.java` (progress + failures)
  - `application/customer/CustomerImportInputFile.java`; `application/customer/exceptions/` (`ImportJobLaunchException`, `MissingInputFileException`)
- Domain:
  - `domain/customer/Customer.java`
  - `domain/customer/policy/CustomerImportPolicy.java`, `EmailAndNameCustomerImportPolicy.java`
  - `domain/validation/package-info.java` (cross-cutting validation placeholder)
- Common: `common/package-info.java` (JDK-only shared helpers)
- Infrastructure:
  - **Batch `@Configuration`**: `infrastructure/batch/config/CustomerImportJobConfig.java`, `CustomerCsvItemReaderConfig.java`
  - **Adapters**: `infrastructure/adapter/batch/` — `SpringBatchCustomerImportUseCase`, processor/writer adapters, `JobCompletionListener`; `infrastructure/adapter/persistence/OracleCustomerUpsertPortAdapter.java`
  - **Config**: `infrastructure/config/` — `AsyncJobLauncherConfig`, `JdbcConfig`, `DomainPolicyConfig`
  - **Diagnostics**: `infrastructure/diagnostics/DevStartupDiagnostics.java`

## Target package structure (approved direction)

This is the proposed target structure for refactor (keeping root package):

```
com.example.spring_batch_demo
├── SpringBatchDemoApplication.java
│
├── common
│   └── package-info.java (optional utilities)
├── domain
│   ├── customer
│   │   ├── Customer.java
│   │   └── policy
│   │       ├── CustomerImportPolicy.java
│   │       └── EmailAndNameCustomerImportPolicy.java
│   └── validation
│       └── package-info.java
├── application
│   └── customer
│       ├── dto
│       │   └── CustomerImportResult.java
│       ├── port
│       │   ├── CustomerImportUseCase.java
│       │   ├── CustomerUpsertPort.java
│       │   └── CustomerSourcePort.java (optional)
│       ├── CustomerImportInputFile.java
│       └── exceptions
│           ├── ImportJobLaunchException.java
│           └── MissingInputFileException.java
├── infrastructure
│   ├── batch
│   │   └── config
│   │       ├── CustomerImportJobConfig.java
│   │       └── CustomerCsvItemReaderConfig.java
│   ├── adapter
│   │   ├── batch
│   │   │   ├── SpringBatchCustomerImportUseCase.java
│   │   │   ├── CustomerItemProcessorAdapter.java
│   │   │   ├── CustomerUpsertItemWriterAdapter.java
│   │   │   └── JobCompletionListener.java
│   │   └── persistence
│   │       └── OracleCustomerUpsertPortAdapter.java
│   ├── config
│   │   ├── AsyncJobLauncherConfig.java
│   │   ├── JdbcConfig.java
│   │   └── DomainPolicyConfig.java
│   └── diagnostics
│       └── DevStartupDiagnostics.java
└── presentation
    └── api
        ├── BatchJobController.java
        └── exceptions
            └── BatchJobApiExceptionHandler.java
```

Notes:
- Spring Batch **wiring** (`Job`/`Step` builders, `@StepScope` reader bean) lives under **`infrastructure.batch.config`**.
- Spring Batch **SPI adapters** and the async use-case implementation live under **`infrastructure.adapter.batch`**.
- Oracle/JDBC port implementation lives under **`infrastructure.adapter.persistence`**.
- **Two “Config” classes in batch:** `CustomerImportJobConfig` owns the **job + fault-tolerant step**; `CustomerCsvItemReaderConfig` owns the **`@StepScope` reader** (separate bean lifecycle from the job graph).
- **Diagnostics** (`DevStartupDiagnostics`): dev-only JDBC probes (current user, table visibility) for operators.
- **Oracle upsert adapter** (`OracleCustomerUpsertPortAdapter`): implements `CustomerUpsertPort` with `MERGE` SQL.
- Domain stays plain Java and can be unit-tested without Spring.

## Data flow (high level)

### Launch (async)
1. HTTP POST → `BatchJobController` → `CustomerImportUseCase.launchImport()`
2. Async `JobLauncher` starts the job in a background thread
3. Controller returns **202 Accepted** with `{jobExecutionId}`

### Execution (fault-tolerant)
4. Batch job runs `customerStep` in chunks of 10:
   - Reader reads CSV → produces `Customer`
   - Processor delegates to domain policy → returns Customer or filters
   - Writer uses application port → infrastructure adapter upserts to Oracle
   - **Retry**: transient DB errors retried 3x with exponential backoff
   - **Skip**: malformed CSV rows skipped (up to 100)

### Status polling
5. HTTP GET → `BatchJobController` → `CustomerImportUseCase.getImportStatus()`
6. `JobExplorer` reads `JobExecution` + `StepExecution` counts → returns progress (`CustomerImportResult`)
7. Controller maps **unknown id → 404**, **`FAILED` status → 500** (same JSON body), **other states → 200**

## Evolution (done vs optional next)

**Already in place:** `domain` (including `domain.customer.policy`), `application` (`port` + `dto` + `customer.exceptions`), `infrastructure.adapter.*` + `infrastructure.batch.config`, `presentation.api` + `presentation.api.exceptions`, and `common` placeholder.

**Optional next steps** (see `ROADMAP.md`):

- `CustomerSourcePort` (abstract CSV behind a port).
- Populate `domain.validation` or `common` when cross-cutting rules or JDK helpers appear.
- Verify changes with `./mvnw clean verify`, Postman/curl, and Oracle queries for `CUSTOMER` and `BATCH_*`.

## Further reading (Onion Architecture)

- Expedia Group Tech: “Onion Architecture — Let’s slice it like a Pro”  
  `https://medium.com/expedia-group-tech/onion-architecture-deed8a554423`
  - Emphasizes **dependency direction** (outer → inner), **data encapsulation**, and choosing a **pragmatic** set of layers.
  - Useful takeaway for this repo: keep Spring Batch + JDBC at the edges; keep inner logic independent and testable.

- DEV Community: “Onion Architecture in Domain-Driven Design (DDD)”  
  `https://dev.to/yasmine_ddec94f4d4/onion-architecture-in-domain-driven-design-ddd-35gn`
  - Frames Onion as a way to **protect the domain model** via **dependency inversion** and clear separation of concerns.
  - Useful takeaway for this repo: define ports/interfaces inward, implement adapters outward.

