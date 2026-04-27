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

They are **not** domain models: they carry **job / polling** concerns (`BatchStatus`, read/skip/write/filter counters). **`Customer`** stays in **domain** as the business row shape. Row-level audit values (`RejectedRow`, `ImportRejectionCategory`) live in **`domain.importaudit`** and are persisted through **`ImportAuditPort`** (application) implemented by JDBC in infrastructure.

## Current code mapping (as-is)

Today, the project is functional and has been refactored into onion-style layers. Key files:

- Presentation:
  - `presentation/api/BatchJobController.java` (POST → 202 enqueue or in-process launch; GET correlation → job id; GET → poll status/report)
  - `presentation/api/exceptions/BatchJobApiExceptionHandler.java` (scoped `ProblemDetail` for this API)
- Application:
  - `application/customer/port/CustomerImportUseCase.java`, `CustomerUpsertPort.java`, `ImportAuditPort.java`, `CustomerImportCommandPublisher.java`, `CustomerImportInputFileValidator.java`, `CustomerImportInputFileStagingPort.java`, `ImportLaunchCorrelationPort.java`
  - `application/customer/dto/CustomerImportResult.java`, `ImportAuditReport.java`, `CustomerImportCommand.java`, `CustomerImportEnqueueResponse.java` (progress + audit + enqueue body)
  - `application/customer/CustomerImportInputFile.java`; `application/customer/exceptions/` (`ImportJobLaunchException`, `InvalidInputFileResourceException`, `MissingInputFileException`, `ImportCommandPublishException`, `InvalidCorrelationIdException`)
- Domain:
  - `domain/customer/Customer.java`
  - `domain/customer/policy/CustomerImportPolicy.java`, `EmailAndNameCustomerImportPolicy.java`
  - `domain/importaudit/` (`ImportRejectionCategory`, `RejectedRow`)
- Infrastructure:
  - **Batch `@Configuration`**: `infrastructure/config/batch/CustomerImportJobConfig.java`, `CustomerCsvItemReaderConfig.java`, `CustomerImportAuditListenerConfig.java`
  - **Adapters**: `infrastructure/adapter/batch/` — `SpringBatchCustomerImportUseCase`, processor/writer adapters, `JobCompletionListener`, `CustomerImportAuditStepListener`; `infrastructure/adapter/persistence/OracleCustomerUpsertPortAdapter.java` (active when profile is not `audit-it` / `amqp-it`), `NoOpCustomerUpsertPortAdapter.java` (`audit-it` / `amqp-it` H2 smoke), `JdbcImportAuditPortAdapter.java`, `JdbcImportLaunchCorrelationAdapter.java`; `infrastructure/adapter/messaging/` — `AmqpCustomerImportCommandPublisher`, `DirectCustomerImportCommandPublisher`, `CustomerImportJobLaunchListener`; `infrastructure/adapter/resource/` — Spring `Resource` validation/resolution and local classpath staging for `inputFile`
  - **Config**: `infrastructure/config/` — `AsyncJobLauncherConfig`, `JdbcConfig`, `DomainPolicyConfig`, `CustomerImportLocalStagingProperties`, `messaging/CustomerImportRabbitConfig.java`, `messaging/MessagingConfigurationBeans.java`, `messaging/CustomerImportMessagingProperties.java`
  - **Diagnostics**: `infrastructure/diagnostics/DevStartupDiagnostics.java`
- Utilities:
  - none at the root package yet; add helpers in the narrowest owning layer/package first, and introduce a shared package only when the abstraction is genuinely cross-layer

## Target package structure (approved direction)

This is the proposed target structure for refactor (keeping root package):

```
com.example.spring_batch_demo
├── SpringBatchDemoApplication.java
│
├── domain
│   ├── customer
│   │   ├── Customer.java
│   │   └── policy
│   │       ├── CustomerImportPolicy.java
│   │       └── EmailAndNameCustomerImportPolicy.java
│   ├── importaudit
│   │   ├── ImportRejectionCategory.java
│   │   └── RejectedRow.java
├── application
│   └── customer
│       ├── dto
│       │   ├── CustomerImportResult.java
│       │   └── ImportAuditReport.java
│       ├── port
│       │   ├── CustomerImportUseCase.java
│       │   ├── CustomerUpsertPort.java
│       │   ├── CustomerImportInputFileValidator.java
│       │   ├── CustomerImportInputFileStagingPort.java
│       │   ├── ImportAuditPort.java
│       │   └── CustomerSourcePort.java (optional)
│       ├── CustomerImportInputFile.java
│       └── exceptions
│           ├── InvalidInputFileResourceException.java
│           ├── ImportJobLaunchException.java
│           ├── InputFileStagingException.java
│           └── MissingInputFileException.java
├── infrastructure
│   ├── adapter
│   │   ├── batch
│   │   │   ├── SpringBatchCustomerImportUseCase.java
│   │   │   ├── CustomerItemProcessorAdapter.java
│   │   │   ├── CustomerUpsertItemWriterAdapter.java
│   │   │   ├── JobCompletionListener.java
│   │   │   └── CustomerImportAuditStepListener.java
│   │   ├── persistence
│   │   │   ├── OracleCustomerUpsertPortAdapter.java
│   │   │   └── JdbcImportAuditPortAdapter.java
│   │   └── resource
│   │       ├── CustomerImportResourceResolver.java
│   │       ├── LocalClasspathCustomerImportInputFileStagingAdapter.java
│   │       └── SpringResourceCustomerImportInputFileValidator.java
│   ├── config
│   │   ├── AsyncJobLauncherConfig.java
│   │   ├── batch
│   │   │   ├── CustomerImportJobConfig.java
│   │   │   ├── CustomerCsvItemReaderConfig.java
│   │   │   └── CustomerImportAuditListenerConfig.java
│   │   ├── CustomerImportLocalStagingProperties.java
│   │   ├── JdbcConfig.java
│   │   ├── DomainPolicyConfig.java
│   │   └── messaging
│   │       ├── CustomerImportRabbitConfig.java
│   │       ├── MessagingConfigurationBeans.java
│   │       └── CustomerImportMessagingProperties.java
│   └── diagnostics
│       └── DevStartupDiagnostics.java
└── presentation
    └── api
        ├── BatchJobController.java
        └── exceptions
            └── BatchJobApiExceptionHandler.java
```

Notes:
- Spring Batch **wiring** (`Job`/`Step` builders, `@StepScope` reader bean) lives under **`infrastructure.config.batch`**.
- Spring Batch **SPI adapters** and the async use-case implementation live under **`infrastructure.adapter.batch`**.
- Oracle/JDBC port implementation lives under **`infrastructure.adapter.persistence`**.
- Spring `Resource` validation/resolution and local classpath staging for `inputFile` live under **`infrastructure.adapter.resource`**.
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
   - **Skip**: configured exception types on read/process/write (e.g. `FlatFileParseException`, line-length / token-count issues, `NumberFormatException`, `DataIntegrityViolationException`) up to shared `skipLimit` 100; each skip can be persisted as `PARSE_SKIP`, `READ_SKIPPED`, `PROCESS_SKIPPED`, or `WRITE_SKIPPED` via `CustomerImportAuditStepListener`

### Status polling
5. HTTP GET → `BatchJobController` → `CustomerImportUseCase.getImportStatus()`
6. `JobExplorer` reads `JobExecution` + `StepExecution` counts → returns progress (`CustomerImportResult`)
7. Controller maps **unknown id → 404**, **`FAILED` status → 500** (same JSON body), **other states → 200**

## Evolution (done vs optional next)

**Already in place:** `domain` (including `domain.customer.policy`), `application` (`port` + `dto` + `customer.exceptions`), `infrastructure.adapter.*`, `infrastructure.config.batch`, `infrastructure.config.messaging`, and `presentation.api` + `presentation.api.exceptions`.

**Optional next steps** (see `ROADMAP.md`):

- `CustomerSourcePort` (abstract CSV behind a port).
- Add utilities in the owning layer/package when duplication becomes real; avoid a root-level `common` package until there is a stable cross-layer abstraction.
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
