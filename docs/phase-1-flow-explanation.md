# Phase 1 flow explanation - direct Spring Batch import

This document captures the Phase 1 style flow in the current codebase: REST accepts an import request, the direct publisher launches Spring Batch in-process, the job reads CSV rows, applies domain rules, writes accepted rows, and exposes job status through Spring Batch metadata.

In the current repository, the `POST` endpoint always uses the `CustomerImportCommandPublisher` port. Phase 1 behavior is the direct branch, active when `app.messaging.customer-import.enabled=false` in [application.properties](../src/main/resources/application.properties) (line 4), [application-audit-it.properties](../src/main/resources/application-audit-it.properties) (line 3), or [application-test.properties](../src/main/resources/application-test.properties) (line 1).

## Phase 1 scope

Phase 1 includes:

- `POST /api/batch/customer/import?inputFile=...`
- direct job launch through `DirectCustomerImportCommandPublisher`
- Spring Batch `customerJob` and `customerStep`
- CSV read, domain policy processing, and customer upsert
- `GET /api/batch/customer/import/{jobExecutionId}/status`
- `CUSTOMER` and Spring Batch `BATCH_*` metadata tables

Phase 1 does not depend on RabbitMQ. It also does not need the Phase 2 report endpoint, although the current codebase now includes audit/report fields because later phases have been added.

## Before any request: service startup and bean registration

When the service starts, the request flow has not happened yet. Spring Boot first builds the runtime graph that will later handle requests and launch jobs.

Chronological startup flow:

1. The JVM enters `SpringBatchDemoApplication.main` in [SpringBatchDemoApplication.java](../src/main/java/com/example/spring_batch_demo/SpringBatchDemoApplication.java) (line 9).
2. `@SpringBootApplication` on [SpringBatchDemoApplication.java](../src/main/java/com/example/spring_batch_demo/SpringBatchDemoApplication.java) (line 6) enables component scanning under `com.example.spring_batch_demo`, Spring Boot auto-configuration, and configuration class processing.
3. Spring loads the active profile and property files. For Phase 1/direct behavior, `app.messaging.customer-import.enabled=false` is set in [application.properties](../src/main/resources/application.properties) (line 4), [application-audit-it.properties](../src/main/resources/application-audit-it.properties) (line 3), or [application-test.properties](../src/main/resources/application-test.properties) (line 1).
4. Spring creates the web application context and embedded web server. Spring MVC registers `DispatcherServlet`, handler mappings, argument resolvers, JSON converters, and exception handling infrastructure.
5. Component scanning registers `BatchJobController` as a REST controller from [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (class line 33). Its endpoint methods are mapped before any request is sent.
6. Component scanning registers `BatchJobApiExceptionHandler` as controller advice from [BatchJobApiExceptionHandler.java](../src/main/java/com/example/spring_batch_demo/presentation/api/exceptions/BatchJobApiExceptionHandler.java) (line 23). This is why `MissingInputFileException`, path variable conversion errors, and launch failures already have HTTP mappings before the first request.
7. Spring Boot creates the `DataSource` from the active datasource properties. For default Oracle-style configuration, those properties are in [application.properties](../src/main/resources/application.properties) (lines 13-16). For `audit-it`, H2 Oracle mode is configured in [application-audit-it.properties](../src/main/resources/application-audit-it.properties) (lines 6-9).
8. SQL initialization is decided by properties. Default mode disables app schema init in [application.properties](../src/main/resources/application.properties) (line 10). `audit-it` runs [schema-h2-import-audit-it.sql](../src/main/resources/schema-h2-import-audit-it.sql) through [application-audit-it.properties](../src/main/resources/application-audit-it.properties) (lines 11-12). `dev` runs Oracle [schema.sql](../src/main/resources/schema.sql) through [application-dev.properties](../src/main/resources/application-dev.properties) (lines 12-16).
9. Spring Batch auto-configuration creates core Batch infrastructure such as `JobRepository`, `JobExplorer`, transaction integration, and metadata access. Batch metadata initialization is controlled by `spring.batch.jdbc.initialize-schema` in [application.properties](../src/main/resources/application.properties) (line 8), [application-audit-it.properties](../src/main/resources/application-audit-it.properties) (line 14), and [application-dev.properties](../src/main/resources/application-dev.properties) (line 18).
10. `spring.batch.job.enabled=false` in [application.properties](../src/main/resources/application.properties) (line 19) and profile files prevents Spring Boot from automatically running `customerJob` at startup. The job is only launched when the API flow calls the use case.
11. Spring registers `asyncJobLauncher` from [AsyncJobLauncherConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/AsyncJobLauncherConfig.java) (line 18). It is a `TaskExecutorJobLauncher` backed by `SimpleAsyncTaskExecutor("batch-")`; the actual `batch-...` worker thread is created later when a job is launched.
12. Spring registers the domain policy bean from `DomainPolicyConfig.customerImportPolicy` in [DomainPolicyConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/DomainPolicyConfig.java) (line 18). The domain class itself remains framework-free.
13. Spring registers `NamedParameterJdbcTemplate` from [JdbcConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/JdbcConfig.java) (line 18). Persistence adapters use it later for customer upsert, audit, and correlation queries.
14. Spring registers the Step-scoped `customerReader` bean definition from [CustomerCsvItemReaderConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/batch/config/CustomerCsvItemReaderConfig.java) (line 30). Because it is `@StepScope`, the actual reader instance is created during a job step, when `inputFile` job parameters exist.
15. Spring registers `CustomerItemProcessorAdapter`, `CustomerUpsertItemWriterAdapter`, `SpringBatchCustomerImportUseCase`, and `JobCompletionListener` by component scanning. These are singleton infrastructure adapters ready to be used by the Batch job.
16. Spring registers `customerJob` and `customerStep` from [CustomerImportJobConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/batch/config/CustomerImportJobConfig.java) (lines 64 and 79). They are definitions of what should run later; they do not process files at startup.
17. Spring evaluates conditional publisher beans. With messaging disabled, it registers `DirectCustomerImportCommandPublisher` from [DirectCustomerImportCommandPublisher.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/messaging/DirectCustomerImportCommandPublisher.java) (line 16). It does not register `AmqpCustomerImportCommandPublisher`.
18. Spring evaluates writer profile conditions. Oracle profiles register `OracleCustomerUpsertPortAdapter` from [OracleCustomerUpsertPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/OracleCustomerUpsertPortAdapter.java) (line 16). `audit-it` and `amqp-it` register `NoOpCustomerUpsertPortAdapter` from [NoOpCustomerUpsertPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/NoOpCustomerUpsertPortAdapter.java) (line 15).

At the end of startup, the service is listening for HTTP requests, endpoint mappings exist, Batch infrastructure exists, the job/step definitions exist, and the direct publisher is ready. No CSV file has been read, no `customerStep` has executed, and no `batch-...` job thread exists until a request launches the job.

## Endpoint 1: POST import, direct successful flow

Request:

```http
POST /api/batch/customer/import?inputFile=classpath:customers.csv
```

Chronological call flow:

1. The request enters Spring MVC and matches `POST /api/batch/customer/import` in [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (method `importCustomers`, line 52).
2. `importCustomers` logs the request and calls `CustomerImportInputFile.requireInputFileLocation(inputFile)` in [CustomerImportInputFile.java](../src/main/java/com/example/spring_batch_demo/application/customer/CustomerImportInputFile.java) (line 19).
3. If `inputFile` is `null` or blank, `requireInputFileLocation` throws `MissingInputFileException`; the flow stops before any job is created.
4. If valid, the controller creates a random `correlationId` using `UUID.randomUUID()`.
5. The controller builds a `CustomerImportCommand` by calling `CustomerImportCommand.of(correlationId, resolvedInput)` in [CustomerImportCommand.java](../src/main/java/com/example/spring_batch_demo/application/customer/dto/CustomerImportCommand.java) (line 25).
6. The controller calls the application port `CustomerImportCommandPublisher.publish(command)` in [CustomerImportCommandPublisher.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/CustomerImportCommandPublisher.java) (line 20).
7. In direct mode, Spring has registered [DirectCustomerImportCommandPublisher.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/messaging/DirectCustomerImportCommandPublisher.java) because its `@ConditionalOnProperty` matches messaging disabled. Its `publish` method starts at line 31.
8. `DirectCustomerImportCommandPublisher.publish` calls `CustomerImportUseCase.launchImport(command.inputFile())` in [CustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/CustomerImportUseCase.java) (line 25).
9. The implementation is [SpringBatchCustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java), method `launchImport`, line 54.
10. `launchImport` validates the input again through `CustomerImportInputFile.requireInputFileLocation`, protecting the application use case even if another caller bypasses the REST controller.
11. `launchImport` creates `JobParameters` with two values: `inputFile` and `run.at`. `run.at` ensures repeated calls with the same input file create distinct Spring Batch job instances.
12. `launchImport` calls `jobLauncher.run(customerJob, params)`. The injected launcher is `asyncJobLauncher`, defined in [AsyncJobLauncherConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/AsyncJobLauncherConfig.java) (method `asyncJobLauncher`, line 18).
13. The launched job is `customerJob`, defined in [CustomerImportJobConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/batch/config/CustomerImportJobConfig.java) (method `customerJob`, line 64).
14. `customerJob` starts `customerStep`, defined in [CustomerImportJobConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/batch/config/CustomerImportJobConfig.java) (method `customerStep`, line 79).
15. `jobLauncher.run` returns a `JobExecution`. `SpringBatchCustomerImportUseCase.launchImport` returns `execution.getId()` to the direct publisher.
16. `DirectCustomerImportCommandPublisher.publish` registers `correlationId -> jobExecutionId` by calling `ImportLaunchCorrelationPort.registerLaunchedJob` in [ImportLaunchCorrelationPort.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/ImportLaunchCorrelationPort.java) (line 10). This correlation capability was added later, but direct mode also writes it now.
17. The JDBC implementation is [JdbcImportLaunchCorrelationAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/JdbcImportLaunchCorrelationAdapter.java), method `registerLaunchedJob`, line 31.
18. `DirectCustomerImportCommandPublisher.publish` returns a `CustomerImportEnqueueResponse` from [CustomerImportEnqueueResponse.java](../src/main/java/com/example/spring_batch_demo/application/customer/dto/CustomerImportEnqueueResponse.java) (line 12), with `status="STARTED"` and a non-null `jobExecutionId`.
19. The controller returns `202 Accepted` with that response body.

Successful response shape:

```json
{
  "correlationId": "2f8f4f22-4c87-48e4-9de9-e53c4f4fe19d",
  "status": "STARTED",
  "jobExecutionId": 41
}
```

Important point: `202 Accepted` means the job was launched, not necessarily completed. The happy flow continues inside Spring Batch, and the client should poll the status endpoint.

## Batch execution flow after POST returns

`customerStep` in [CustomerImportJobConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/batch/config/CustomerImportJobConfig.java) (line 79) is configured as:

- chunk size `10`
- reader: `FlatFileItemReader<Customer>`
- processor: `ItemProcessor<Customer, Customer>`
- writer: `ItemWriter<Customer>`
- retry for `TransientDataAccessException`, retry limit `3`
- exponential backoff: `1000ms`, multiplier `2.0`, max `8000ms`
- skip for `FlatFileParseException`, `IncorrectLineLengthException`, `IncorrectTokenCountException`, `NumberFormatException`, and `DataIntegrityViolationException`
- skip limit `100`

Chronological batch flow:

1. Spring Batch starts `customerJob`.
2. `JobCompletionListener.beforeJob` in [JobCompletionListener.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/JobCompletionListener.java) runs before the job (line 15).
3. Spring Batch starts `customerStep`.
4. The step asks the reader for rows. The reader bean is `customerReader` in [CustomerCsvItemReaderConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/batch/config/CustomerCsvItemReaderConfig.java) (line 30).
5. `customerReader` is `@StepScope`, so it reads the `inputFile` job parameter at runtime.
6. `customerReader` validates the job parameter through `CustomerImportInputFile.requireJobParameterInputFile` in [CustomerImportInputFile.java](../src/main/java/com/example/spring_batch_demo/application/customer/CustomerImportInputFile.java) (line 30).
7. The reader loads the resource through Spring's `ResourceLoader`. Examples are `classpath:customers.csv` and `file:/tmp/customers.csv`.
8. For each line, the reader maps fields named `id`, `name`, and `email` into the domain record [Customer.java](../src/main/java/com/example/spring_batch_demo/domain/customer/Customer.java) (line 9).
9. Spring Batch calls `CustomerItemProcessorAdapter.process(customer)` in [CustomerItemProcessorAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/CustomerItemProcessorAdapter.java) (line 22).
10. The processor delegates to the domain port `CustomerImportPolicy.apply(customer)` in [CustomerImportPolicy.java](../src/main/java/com/example/spring_batch_demo/domain/customer/policy/CustomerImportPolicy.java) (line 10).
11. The configured policy bean is created by `DomainPolicyConfig.customerImportPolicy` in [DomainPolicyConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/DomainPolicyConfig.java) (line 18).
12. The implementation is `EmailAndNameCustomerImportPolicy.apply` in [EmailAndNameCustomerImportPolicy.java](../src/main/java/com/example/spring_batch_demo/domain/customer/policy/EmailAndNameCustomerImportPolicy.java) (line 17).
13. If the input customer is `null`, has `null` email, or the email does not contain `@`, the policy returns `null`.
14. Spring Batch interprets processor `null` as a filtered row. It increments `filterCount` and does not call the writer for that row.
15. If the email is valid, the policy uppercases `name` and returns a new `Customer`.
16. Spring Batch collects processed customers into a chunk of up to `10`.
17. Spring Batch calls `CustomerUpsertItemWriterAdapter.write(chunk)` in [CustomerUpsertItemWriterAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/CustomerUpsertItemWriterAdapter.java) (line 24).
18. The writer adapts Spring Batch's `Chunk` into the application port `CustomerUpsertPort.upsert(customers)` in [CustomerUpsertPort.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/CustomerUpsertPort.java) (line 19).
19. In normal Oracle profiles, the implementation is `OracleCustomerUpsertPortAdapter.upsert` in [OracleCustomerUpsertPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/OracleCustomerUpsertPortAdapter.java) (line 34).
20. `OracleCustomerUpsertPortAdapter` executes a batch `MERGE INTO CUSTOMER`, updating existing IDs and inserting new IDs.
21. In `audit-it` and `amqp-it`, the implementation is `NoOpCustomerUpsertPortAdapter.upsert` in [NoOpCustomerUpsertPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/NoOpCustomerUpsertPortAdapter.java) (line 19), so smoke/integration tests do not require Oracle customer writes.
22. Spring Batch commits the chunk transaction.
23. Spring Batch repeats read/process/write until the input resource is exhausted or the job fails.
24. `JobCompletionListener.afterJob` in [JobCompletionListener.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/JobCompletionListener.java) runs after completion/failure (line 23).

## Where the happy flow goes ahead

The successful POST flow goes ahead into the asynchronous Spring Batch job:

```text
HTTP POST
  -> BatchJobController.importCustomers
  -> DirectCustomerImportCommandPublisher.publish
  -> SpringBatchCustomerImportUseCase.launchImport
  -> asyncJobLauncher.run
  -> customerJob
  -> customerStep
  -> customerReader
  -> CustomerItemProcessorAdapter
  -> EmailAndNameCustomerImportPolicy
  -> CustomerUpsertItemWriterAdapter
  -> OracleCustomerUpsertPortAdapter / NoOpCustomerUpsertPortAdapter
  -> CUSTOMER + BATCH_* metadata
  -> GET status polling
```

The final successful business result is observed by polling status until `status` becomes `COMPLETED`.

## Endpoint 2: GET status flow

Request:

```http
GET /api/batch/customer/import/{jobExecutionId}/status
```

Chronological call flow:

1. The request enters `BatchJobController.getImportStatus` in [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (line 98).
2. Spring MVC converts `{jobExecutionId}` to `Long`. If conversion fails, `BatchJobApiExceptionHandler.onBadPathVariable` in [BatchJobApiExceptionHandler.java](../src/main/java/com/example/spring_batch_demo/presentation/api/exceptions/BatchJobApiExceptionHandler.java) returns `400` (line 71).
3. The controller calls `CustomerImportUseCase.getImportStatus(jobExecutionId)` in [CustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/CustomerImportUseCase.java) (line 33).
4. The implementation is `SpringBatchCustomerImportUseCase.getImportStatus` in [SpringBatchCustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java) (line 74).
5. The use case calls `JobExplorer.getJobExecution(jobExecutionId)` to load Spring Batch metadata.
6. If `JobExplorer` returns `null`, the use case returns `null`.
7. If the controller receives `null`, it returns `404 Not Found`.
8. If the execution exists, the use case resolves failure messages through `resolveFailureMessages` in [SpringBatchCustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java) (line 135).
9. The use case iterates `StepExecution` records and sums `readCount`, `writeCount`, `skipCount`, and `filterCount`.
10. In the current Phase 2-aware code, the use case also loads a rejected sample with `ImportAuditPort.loadRows(jobExecutionId, 10, 0)` in [ImportAuditPort.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/ImportAuditPort.java) (line 27). In pure Phase 1 this field would be empty/not central.
11. The use case creates `CustomerImportResult` in [CustomerImportResult.java](../src/main/java/com/example/spring_batch_demo/application/customer/dto/CustomerImportResult.java) (line 13).
12. If `result.status()` is `FAILED`, the controller returns `500 Internal Server Error` with the result body.
13. Otherwise, the controller returns `200 OK` with the result body.

Example successful response:

```json
{
  "jobExecutionId": 41,
  "status": "COMPLETED",
  "failures": [],
  "readCount": 100,
  "writeCount": 95,
  "skipCount": 2,
  "filterCount": 3,
  "rejectedSample": []
}
```

## Exception and response mapping

| Situation | Where it occurs | Final response |
|-----------|-----------------|----------------|
| Missing/blank `inputFile` | `CustomerImportInputFile.requireInputFileLocation` in [CustomerImportInputFile.java](../src/main/java/com/example/spring_batch_demo/application/customer/CustomerImportInputFile.java) (line 19) | `400 ProblemDetail`, handled by `onMissingInputFile` in [BatchJobApiExceptionHandler.java](../src/main/java/com/example/spring_batch_demo/presentation/api/exceptions/BatchJobApiExceptionHandler.java) (line 27) |
| Job launch fails | `SpringBatchCustomerImportUseCase.launchImport` in [SpringBatchCustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java) (line 54) | `500 ProblemDetail`, handled by `onImportLaunchFailed` in [BatchJobApiExceptionHandler.java](../src/main/java/com/example/spring_batch_demo/presentation/api/exceptions/BatchJobApiExceptionHandler.java) (line 38) |
| Path variable is not numeric | Spring MVC conversion before `getImportStatus` | `400 ProblemDetail`, handled by `onBadPathVariable` in [BatchJobApiExceptionHandler.java](../src/main/java/com/example/spring_batch_demo/presentation/api/exceptions/BatchJobApiExceptionHandler.java) (line 71) |
| Unknown job execution id | `JobExplorer.getJobExecution` returns `null` in [SpringBatchCustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java) (line 74) | `404 Not Found` |
| Batch status is `FAILED` | `BatchJobController.getImportStatus` in [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (line 98) | `500` with `CustomerImportResult` body |
| Unexpected controller error | fallback handler | `500 ProblemDetail`, handled by `onUnexpected` in [BatchJobApiExceptionHandler.java](../src/main/java/com/example/spring_batch_demo/presentation/api/exceptions/BatchJobApiExceptionHandler.java) (line 88) |

## Database objects used in Phase 1

Application table:

- `CUSTOMER`, created in [schema.sql](../src/main/resources/schema.sql) (line 7) and [schema-h2-import-audit-it.sql](../src/main/resources/schema-h2-import-audit-it.sql) (line 1).

Spring Batch metadata:

- `BATCH_JOB_INSTANCE`
- `BATCH_JOB_EXECUTION`
- `BATCH_STEP_EXECUTION`
- other `BATCH_*` tables created by Spring Batch initialization

`CUSTOMER` stores accepted business rows. `BATCH_*` stores execution status, counters, and exit metadata used by the status endpoint.
