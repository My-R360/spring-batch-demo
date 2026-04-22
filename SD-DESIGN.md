# SD: Design (Patterns + SOLID)

This document explains key design choices and where they appear in the current onion-architecture implementation.

## SOLID principles

### Single Responsibility Principle (SRP)

- `BatchJobController` handles HTTP request/response and delegates work.
  - `src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java`
- `CustomerCsvItemReaderConfig` builds and configures the CSV reader bean (`@StepScope`).
  - `src/main/java/com/example/spring_batch_demo/infrastructure/batch/config/CustomerCsvItemReaderConfig.java`
- `CustomerItemProcessorAdapter` only bridges Batch processor SPI to domain policy.
  - `src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/CustomerItemProcessorAdapter.java`
- `EmailAndNameCustomerImportPolicy` contains business rule logic (email validity + uppercase name).
  - `src/main/java/com/example/spring_batch_demo/domain/customer/policy/EmailAndNameCustomerImportPolicy.java`
- `OracleCustomerUpsertPortAdapter` contains Oracle/JDBC persistence logic only.
  - `src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/OracleCustomerUpsertPortAdapter.java`

### Open/Closed Principle (OCP)

- Business rule is behind `CustomerImportPolicy`; new policies can be added without changing batch adapter flow.
  - `src/main/java/com/example/spring_batch_demo/domain/customer/CustomerImportPolicy.java`

### Liskov Substitution Principle (LSP)

- Any `CustomerImportPolicy` implementation can replace another if it honors contract: return transformed `Customer` or `null` to filter.
- Any `CustomerUpsertPort` implementation can replace Oracle adapter while keeping application layer unchanged.

### Interface Segregation Principle (ISP)

- `CustomerImportPolicy` (under `domain.customer.policy`) has a single focused method.
- `CustomerUpsertPort` has a single focused responsibility for upsert operations.

### Dependency Inversion Principle (DIP)

- Application layer depends on `CustomerUpsertPort` abstraction, not JDBC implementation.
- Infrastructure provides adapter implementation of that port.

## Design patterns

### Strategy

- Interface: `CustomerImportPolicy`
- Concrete strategy: `EmailAndNameCustomerImportPolicy`
- Context: `CustomerItemProcessorAdapter`

### Adapter

- `CustomerItemProcessorAdapter`: domain policy -> `ItemProcessor` SPI
- `CustomerUpsertItemWriterAdapter`: port -> `ItemWriter` SPI
- `OracleCustomerUpsertPortAdapter`: `CustomerUpsertPort` -> Oracle JDBC

### Template Method (Spring Batch)

- Framework controls chunk algorithm: read -> process -> write.
- Project provides reader/processor/writer implementations.
- Fault-tolerant step adds retry + skip policies around the chunk loop.

### Builder

- `JobBuilder` and `StepBuilder` for job/step wiring.
- `FlatFileItemReaderBuilder` for CSV reader creation.

### Observer/Listener

- `JobCompletionListener` logs before/after job lifecycle events including per-step read/write/skip/rollback/commit counts.
  - `src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/JobCompletionListener.java`
- `CustomerImportAuditStepListener` (registered as both `SkipListener` and `ItemProcessListener` on the fault-tolerant step) persists `PARSE_SKIP`, `READ_SKIPPED`, `PROCESS_SKIPPED`, `WRITE_SKIPPED`, and `POLICY_FILTER` rows through `ImportAuditPort` (`REQUIRES_NEW` inserts survive chunk rollbacks; insert failures propagate and fail the step).
  - `src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/CustomerImportAuditStepListener.java`

### Async execution (Phase 1)

- `AsyncJobLauncherConfig` provides a `TaskExecutorJobLauncher` with `SimpleAsyncTaskExecutor` so that `JobLauncher.run()` returns immediately while the job executes in a background thread.
  - `src/main/java/com/example/spring_batch_demo/infrastructure/config/AsyncJobLauncherConfig.java`
- Launch failures are wrapped as **`ImportJobLaunchException`** (`application.customer.exceptions`, no Spring Batch types on the port). `presentation.api.exceptions.BatchJobApiExceptionHandler` maps them to **`ProblemDetail`** (HTTP 500) while keeping the controller thin.
- The use-case interface is split into `launchImport` (fire-and-forget, returns `jobExecutionId`) and `getImportStatus` (reads progress via `JobExplorer`).
- The controller returns **202 Accepted** on POST and exposes a GET status endpoint.
- **GET status / GET report HTTP semantics**: **404** if the execution id is unknown; **500** when batch `status` is `FAILED` (response body remains `CustomerImportResult` or `ImportAuditReport`); **200** for `STARTED` / `COMPLETED` / etc., including `COMPLETED` with a non-empty `failures` list on status when the job exited successfully but logged warnings.
- `getImportStatus` builds `failures` from persisted **exit descriptions** on the job and on any `FAILED` steps (`JobExecution#getAllFailureExceptions()` is not reloaded from the database when using `JobExplorer`). It also aggregates `filterCount` and loads a small `rejectedSample` from `ImportAuditPort`.
- `GET .../report` returns `ImportAuditReport` (job status, total rejected count, paginated `RejectedRow` list) with the same **404** / **500-on-FAILED** HTTP mapping as status polling.

### Messaging boundary (Phase 3)

- **`CustomerImportCommand`** (`application.customer.dto`) is the JSON-serializable command: `correlationId` (UUID), `inputFile`, `schemaVersion`.
- **`CustomerImportCommandPublisher`** (`application.customer.port`) is implemented either by **`AmqpCustomerImportCommandPublisher`** (profile `dev`: publish to RabbitMQ, return `QUEUED` + null `jobExecutionId`) or **`DirectCustomerImportCommandPublisher`** (messaging **off**: call `launchImport` on the HTTP thread, return `STARTED` + `jobExecutionId`).
- **`CustomerImportInputFileValidator`** (`application.customer.port`) keeps resource availability as a port contract; `SpringResourceCustomerImportInputFileValidator` verifies that `classpath:` / `file:` / plain local path inputs exist and are readable before a command is queued.
- **`CustomerImportInputFileStagingPort`** (`application.customer.port`) prepares local filesystem inputs for command hand-off. `LocalClasspathCustomerImportInputFileStagingAdapter` copies them to `target/classes/customer-imports/` and returns `classpath:customer-imports/...`; `classpath:` inputs pass through unchanged.
- **`ImportLaunchCorrelationPort`** + **`JdbcImportLaunchCorrelationAdapter`** persist `correlation_id` → `job_execution_id` in **`IMPORT_LAUNCH_CORRELATION`** after a successful launch (insert-if-absent for safe redelivery semantics).
- **`CustomerImportRabbitConfig`** (`infrastructure.config.messaging`, active when `app.messaging.customer-import.enabled=true`) declares direct exchange, work queue with **DLX** to **`customer.import.dlq`**, `Jackson2JsonMessageConverter`, `RabbitTemplateCustomizer`, and a **`SimpleRabbitListenerContainerFactory`** with **prefetch=1**, stateless **retry** + **`RejectAndDontRequeueRecoverer`**, and `defaultRequeueRejected=false`.
- **`CustomerImportJobLaunchListener`** (`@RabbitListener`, ack mode **AUTO**) invokes **`CustomerImportUseCase.launchImport`** with the staged location then registers correlation — same batch semantics as Phases 1–2; only the **trigger path** moved behind the broker in `dev`.
- **HTTP errors:** **`ImportCommandPublishException`** maps to **503** (`ProblemDetail`); invalid UUID on `GET .../by-correlation/...` maps to **400** (`InvalidCorrelationIdException`); missing/unreadable input files map to **400** before RabbitMQ publish.

### Fault tolerance (Phase 1)

- `customerStep` is configured as fault-tolerant:
  - **Retry**: `TransientDataAccessException` up to 3 times with exponential backoff (1s initial, 2x multiplier, 8s max).
  - **Skip** (shared `skipLimit` 100): read mapping issues (`FlatFileParseException`, `IncorrectLineLengthException`, `IncorrectTokenCountException`, `NumberFormatException`) and write integrity (`DataIntegrityViolationException`) where applicable; each skip is audited when the listener runs.
- This means transient DB hiccups don't kill the job, and many bad CSV rows are counted and audited without halting the whole job until `skipLimit` is exceeded.

## Constructor injection gotcha: `@Qualifier` + Lombok

Lombok's `@RequiredArgsConstructor` generates a constructor from `final` fields, but it does **not** propagate field-level `@Qualifier` annotations to the constructor parameters. When multiple beans of the same type exist (e.g. multiple `Job` beans), Spring fails with `NoUniqueBeanDefinitionException`.

**Rule**: When a `@Qualifier` is needed, write an explicit constructor instead of relying on `@RequiredArgsConstructor`.

Affected file (fixed): `infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java`

## Immutable value objects (Java records)

- `Customer` is a Java `record` — immutable, no setters, equality/hashCode/toString generated by the JVM.
  - `src/main/java/com/example/spring_batch_demo/domain/customer/Customer.java`
- `CustomerImportResult` and `ImportAuditReport` are `record` types in the application layer (DTO package).
  - `src/main/java/com/example/spring_batch_demo/application/customer/dto/CustomerImportResult.java`
  - `src/main/java/com/example/spring_batch_demo/application/customer/dto/ImportAuditReport.java`
- Domain policies (e.g. `EmailAndNameCustomerImportPolicy`) return a **new** record instance instead of mutating the input, preserving immutability throughout the batch pipeline.
- The CSV reader uses a custom `FieldSetMapper` to construct records (since `BeanWrapperFieldSetMapper` requires setters).

## Persistence notes

- Oracle upsert uses `MERGE`, so reruns update existing IDs instead of failing with ORA-00001.
  - `src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/OracleCustomerUpsertPortAdapter.java`

## Diagnostics

- `DevStartupDiagnostics` reports DB user and table visibility in `dev`.
  - `src/main/java/com/example/spring_batch_demo/infrastructure/diagnostics/DevStartupDiagnostics.java`

## Startup logging (Spring Batch + JDBC)

- On **Spring Boot 3.2.x**, context refresh may log **WARN** from `PostProcessorRegistrationDelegate` about beans “not eligible for getting processed by all BeanPostProcessors” (often involving `JobRegistryBeanPostProcessor`, `DataSource`, `PlatformTransactionManager`). This is a known ordering quirk between **Batch auto-configuration** and the JDBC stack; it is **usually harmless** if the app starts and batch jobs execute.
- Prefer fixing only when startup **actually fails**; see `RUNBOOK.md` troubleshooting for this topic.
