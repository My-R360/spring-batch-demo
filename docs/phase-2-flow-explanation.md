# Phase 2 flow explanation - audit and reporting

Phase 2 adds rejected-row audit and reporting on top of the Phase 1 import job. The core import path still reads CSV, applies the domain policy, and writes valid rows, but now read/process/write skips and policy-filtered rows are persisted to `IMPORT_REJECTED_ROW`.

The main user-visible additions are:

- status includes `filterCount`
- new report endpoint returns paginated rejected rows
- `IMPORT_REJECTED_ROW` stores category, line number, reason, and source fields

## Before any request: service startup and audit wiring

Phase 2 startup is Phase 1 startup plus audit/report infrastructure. The key point is that Spring registers audit components before any import request, but actual rejected rows are written only during a running `customerStep`.

Chronological startup flow:

1. The JVM enters `SpringBatchDemoApplication.main` in [SpringBatchDemoApplication.java](../src/main/java/com/example/spring_batch_demo/SpringBatchDemoApplication.java) (line 9), and `@SpringBootApplication` (line 6) starts component scanning and auto-configuration under the root package.
2. Spring loads properties and profiles. The Phase 2 smoke path normally uses `audit-it`, where messaging is disabled in [application-audit-it.properties](../src/main/resources/application-audit-it.properties) (line 3) and Rabbit auto-configuration is excluded (line 4).
3. Spring creates the web application context, embedded web server, Spring MVC `DispatcherServlet`, request mappings, JSON conversion, and exception handler infrastructure.
4. Spring registers `BatchJobController` from [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (class line 33). This includes the POST, correlation lookup, status, and report mappings.
5. Spring registers `BatchJobApiExceptionHandler` from [BatchJobApiExceptionHandler.java](../src/main/java/com/example/spring_batch_demo/presentation/api/exceptions/BatchJobApiExceptionHandler.java) (line 23). Error mappings exist before the first request.
6. Spring creates the `DataSource`. In `audit-it`, this is H2 Oracle mode from [application-audit-it.properties](../src/main/resources/application-audit-it.properties) (lines 6-9). In Oracle profiles, datasource settings come from [application.properties](../src/main/resources/application.properties) (lines 13-16) or [application-dev.properties](../src/main/resources/application-dev.properties) (lines 22-25).
7. Spring SQL initialization creates application tables. In `audit-it`, [schema-h2-import-audit-it.sql](../src/main/resources/schema-h2-import-audit-it.sql) creates `CUSTOMER` (line 1), `IMPORT_REJECTED_ROW` (line 7), and `IMPORT_LAUNCH_CORRELATION` (line 18). In `dev`, [schema.sql](../src/main/resources/schema.sql) creates `CUSTOMER` (line 7), `IMPORT_REJECTED_ROW` (line 24), and `IMPORT_LAUNCH_CORRELATION` (line 46).
8. Spring Batch auto-configuration creates `JobRepository`, `JobExplorer`, and metadata access. Batch metadata schema initialization is enabled for embedded H2 in [application-audit-it.properties](../src/main/resources/application-audit-it.properties) (line 14).
9. `spring.batch.job.enabled=false` in [application-audit-it.properties](../src/main/resources/application-audit-it.properties) (line 16) prevents `customerJob` from running at startup.
10. Spring registers `asyncJobLauncher` from [AsyncJobLauncherConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/AsyncJobLauncherConfig.java) (line 18). A worker thread is not created until a request launches a job.
11. Spring registers `CustomerImportPolicy` from [DomainPolicyConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/DomainPolicyConfig.java) (line 18), `NamedParameterJdbcTemplate` from [JdbcConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/JdbcConfig.java) (line 18), and persistence adapters by component scanning.
12. Spring registers `JdbcImportAuditPortAdapter` from [JdbcImportAuditPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/JdbcImportAuditPortAdapter.java) (class line 18). Its constructor creates a `TransactionTemplate` configured with `PROPAGATION_REQUIRES_NEW`, so audit inserts can use separate transactions when the job runs.
13. Spring registers the Step-scoped `CustomerImportAuditStepListener` bean definition from [CustomerImportAuditListenerConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/batch/CustomerImportAuditListenerConfig.java) (line 16). Because it is `@StepScope`, the listener instance is resolved during step execution with the current reader and audit port.
14. Spring registers the Step-scoped `customerReader` bean definition from [CustomerCsvItemReaderConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/batch/CustomerCsvItemReaderConfig.java) (line 30). The actual reader waits for job parameters.
15. Spring registers `customerJob` and `customerStep` from [CustomerImportJobConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/batch/CustomerImportJobConfig.java) (lines 64 and 79). During this wiring, the step is configured with the audit listener as both `SkipListener` and `ItemProcessListener`.
16. Spring evaluates publisher conditions. With `audit-it`, it registers `DirectCustomerImportCommandPublisher` from [DirectCustomerImportCommandPublisher.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/messaging/DirectCustomerImportCommandPublisher.java) (line 16) and does not register AMQP publisher/listener beans.
17. Spring evaluates writer profiles. `audit-it` registers `NoOpCustomerUpsertPortAdapter` from [NoOpCustomerUpsertPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/NoOpCustomerUpsertPortAdapter.java) (line 15), so Phase 2 smoke can verify batch/audit behavior without Oracle customer MERGE.

At the end of startup, the report endpoint exists, the audit table exists, the audit JDBC adapter exists, and `customerStep` knows which listener to call. No audit rows exist until a job execution actually reads, filters, or skips rows.

## Phase 2 data and contracts

Important DTOs and domain types:

- `CustomerImportResult` in [CustomerImportResult.java](../src/main/java/com/example/spring_batch_demo/application/customer/dto/CustomerImportResult.java) (line 13) now includes `filterCount`.
- `ImportAuditReport` in [ImportAuditReport.java](../src/main/java/com/example/spring_batch_demo/application/customer/dto/ImportAuditReport.java) (line 10) is the report response body.
- `RejectedRow` in [RejectedRow.java](../src/main/java/com/example/spring_batch_demo/domain/importaudit/RejectedRow.java) (line 13) is the audit row shape.
- `ImportRejectionCategory` in [ImportRejectionCategory.java](../src/main/java/com/example/spring_batch_demo/domain/importaudit/ImportRejectionCategory.java) (line 6) defines the reason categories.
- `ImportAuditPort` in [ImportAuditPort.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/ImportAuditPort.java) (line 10) is the application port for audit persistence.

Rejection categories:

| Category | Created by | Meaning |
|----------|------------|---------|
| `PARSE_SKIP` | `onSkipInRead` when exception is `FlatFileParseException` | CSV row could not be parsed |
| `READ_SKIPPED` | `onSkipInRead` for other read failures | reader skipped the row |
| `PROCESS_SKIPPED` | `onSkipInProcess` | processor threw a skippable exception |
| `WRITE_SKIPPED` | `onSkipInWrite` | writer threw a skippable exception |
| `POLICY_FILTER` | `afterProcess` when result is `null` | domain policy rejected row without failing the step |

## Phase 2 successful POST flow

Request:

```http
POST /api/batch/customer/import?inputFile=classpath:customers-phase2-audit-sample.csv
```

The request path starts the same way as Phase 1:

1. Spring MVC routes to `BatchJobController.importCustomers` in [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (line 52).
2. The controller validates `inputFile` through `CustomerImportInputFile.requireInputFileLocation` in [CustomerImportInputFile.java](../src/main/java/com/example/spring_batch_demo/application/customer/CustomerImportInputFile.java) (line 19).
3. The controller creates a `correlationId` and a `CustomerImportCommand` through `CustomerImportCommand.of` in [CustomerImportCommand.java](../src/main/java/com/example/spring_batch_demo/application/customer/dto/CustomerImportCommand.java) (line 25).
4. The controller calls `CustomerImportCommandPublisher.publish` in [CustomerImportCommandPublisher.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/CustomerImportCommandPublisher.java) (line 20).
5. In the Phase 2 local smoke profile (`audit-it`), [DirectCustomerImportCommandPublisher.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/messaging/DirectCustomerImportCommandPublisher.java) handles `publish` (line 31).
6. The direct publisher calls `CustomerImportUseCase.launchImport` in [CustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/CustomerImportUseCase.java) (line 25).
7. [SpringBatchCustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java) launches the job in `launchImport` (line 54).
8. Spring Batch executes `customerJob` from [CustomerImportJobConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/batch/CustomerImportJobConfig.java) (line 64) and `customerStep` (line 79).
9. The controller returns `202 Accepted` with `CustomerImportEnqueueResponse` from [CustomerImportEnqueueResponse.java](../src/main/java/com/example/spring_batch_demo/application/customer/dto/CustomerImportEnqueueResponse.java) (line 12).

In `audit-it`, success response normally has `status="STARTED"` and a non-null `jobExecutionId`.

## Phase 2 batch flow with audit hooks

The Phase 2 value starts inside `customerStep`.

Chronological batch and audit flow:

1. Spring Batch starts `customerStep` in [CustomerImportJobConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/batch/CustomerImportJobConfig.java) (line 79).
2. The step reads rows through `customerReader` in [CustomerCsvItemReaderConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/batch/CustomerCsvItemReaderConfig.java) (line 30).
3. If read succeeds, the reader maps the row to `Customer` in [Customer.java](../src/main/java/com/example/spring_batch_demo/domain/customer/Customer.java) (line 9).
4. The step calls `CustomerItemProcessorAdapter.process` in [CustomerItemProcessorAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/CustomerItemProcessorAdapter.java) (line 22).
5. The processor delegates to `EmailAndNameCustomerImportPolicy.apply` in [EmailAndNameCustomerImportPolicy.java](../src/main/java/com/example/spring_batch_demo/domain/customer/policy/EmailAndNameCustomerImportPolicy.java) (line 17).
6. If the policy returns a non-null `Customer`, the row continues to the writer.
7. If the policy returns `null`, Spring Batch treats the row as filtered and increments `filterCount`.
8. The Phase 2 listener `CustomerImportAuditStepListener.afterProcess` in [CustomerImportAuditStepListener.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/CustomerImportAuditStepListener.java) runs after processing (line 93).
9. When `afterProcess` sees `item != null` and `result == null`, it creates a `RejectedRow` with category `POLICY_FILTER`.
10. The listener gets the current job id through `requireJobExecutionId` in [CustomerImportAuditStepListener.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/CustomerImportAuditStepListener.java) (line 138).
11. The listener tries to determine line number through `lineNumberOrNull` in [CustomerImportAuditStepListener.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/CustomerImportAuditStepListener.java) (line 146).
12. The listener calls `ImportAuditPort.recordRejected` in [ImportAuditPort.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/ImportAuditPort.java) (line 16).
13. The implementation is `JdbcImportAuditPortAdapter.recordRejected` in [JdbcImportAuditPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/JdbcImportAuditPortAdapter.java) (line 57).
14. `recordRejected` runs the insert in a `REQUIRES_NEW` transaction, then calls `insertRow` in [JdbcImportAuditPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/JdbcImportAuditPortAdapter.java) (line 63).
15. `insertRow` writes into `IMPORT_REJECTED_ROW`.
16. If the row was valid, Spring Batch calls `CustomerUpsertItemWriterAdapter.write` in [CustomerUpsertItemWriterAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/CustomerUpsertItemWriterAdapter.java) (line 24).
17. The writer calls `CustomerUpsertPort.upsert` in [CustomerUpsertPort.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/CustomerUpsertPort.java) (line 19).
18. Oracle profile writes through `OracleCustomerUpsertPortAdapter.upsert` in [OracleCustomerUpsertPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/OracleCustomerUpsertPortAdapter.java) (line 34). `audit-it` uses `NoOpCustomerUpsertPortAdapter.upsert` in [NoOpCustomerUpsertPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/NoOpCustomerUpsertPortAdapter.java) (line 19).

## Read/process/write skip audit branches

Phase 2 also records skippable exceptions:

1. If the reader throws `FlatFileParseException`, Spring Batch skip handling calls `CustomerImportAuditStepListener.onSkipInRead` in [CustomerImportAuditStepListener.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/CustomerImportAuditStepListener.java) (line 24).
2. `onSkipInRead` records category `PARSE_SKIP`, the line number from the exception, the exception message, and truncated raw input.
3. If read skip is not a `FlatFileParseException`, `onSkipInRead` records category `READ_SKIPPED`.
4. If the processor throws a skippable exception, Spring Batch calls `onSkipInProcess` in [CustomerImportAuditStepListener.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/CustomerImportAuditStepListener.java) (line 54).
5. `onSkipInProcess` records category `PROCESS_SKIPPED`, source id/name/email, line number when available, and a reason from `ImportRejectionReasons.processSkippedDetail` in [ImportRejectionReasons.java](../src/main/java/com/example/spring_batch_demo/application/customer/audit/ImportRejectionReasons.java) (line 24).
6. If the writer throws a skippable exception, Spring Batch calls `onSkipInWrite` in [CustomerImportAuditStepListener.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/CustomerImportAuditStepListener.java) (line 71).
7. `onSkipInWrite` records category `WRITE_SKIPPED` and reason from `ImportRejectionReasons.writeSkippedDetail` in [ImportRejectionReasons.java](../src/main/java/com/example/spring_batch_demo/application/customer/audit/ImportRejectionReasons.java) (line 28).
8. Every audit branch calls `ImportAuditPort.recordRejected`, which ends at `JdbcImportAuditPortAdapter.insertRow`.

## Endpoint 1: GET status

Request:

```http
GET /api/batch/customer/import/{jobExecutionId}/status
```

Chronological call flow:

1. Spring MVC routes to `BatchJobController.getImportStatus` in [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (line 98).
2. The controller calls `CustomerImportUseCase.getImportStatus` in [CustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/CustomerImportUseCase.java) (line 33).
3. The implementation `SpringBatchCustomerImportUseCase.getImportStatus` starts at [SpringBatchCustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java) (line 74).
4. It calls `JobExplorer.getJobExecution(jobExecutionId)`.
5. If the execution does not exist, it returns `null`; the controller returns `404`.
6. If execution exists, it resolves failure messages via `resolveFailureMessages` in [SpringBatchCustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java) (line 135).
7. It sums step-level `readCount`, `writeCount`, `skipCount`, and `filterCount`.
8. The use case returns `CustomerImportResult` in [CustomerImportResult.java](../src/main/java/com/example/spring_batch_demo/application/customer/dto/CustomerImportResult.java) (line 13).
9. The controller returns `500` if status is `FAILED`; otherwise it returns `200`.

Example response:

```json
{
  "jobExecutionId": 41,
  "status": "COMPLETED",
  "failures": [],
  "readCount": 100,
  "writeCount": 95,
  "skipCount": 2,
  "filterCount": 3
}
```

## Endpoint 2: GET report

Request:

```http
GET /api/batch/customer/import/{jobExecutionId}/report?limit=50&offset=0
```

Chronological call flow:

1. Spring MVC routes to `BatchJobController.getImportAuditReport` in [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (line 121).
2. The controller calls `CustomerImportUseCase.getImportAuditReport(jobExecutionId, limit, offset)` in [CustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/CustomerImportUseCase.java) (line 43).
3. The implementation is `SpringBatchCustomerImportUseCase.getImportAuditReport` in [SpringBatchCustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java) (line 109).
4. The use case calls `JobExplorer.getJobExecution(jobExecutionId)`.
5. If no execution exists, the use case returns `null`; the controller returns `404`.
6. If execution exists, the use case clamps `limit` to `1..500` and clamps `offset` to `>= 0`.
7. It calls `ImportAuditPort.countRejected(jobExecutionId)` in [ImportAuditPort.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/ImportAuditPort.java) (line 18).
8. `JdbcImportAuditPortAdapter.countRejected` in [JdbcImportAuditPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/JdbcImportAuditPortAdapter.java) (line 76) executes `SELECT COUNT(*)`.
9. It calls `ImportAuditPort.loadRows(jobExecutionId, safeLimit, safeOffset)` in [ImportAuditPort.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/ImportAuditPort.java) (line 27).
10. `JdbcImportAuditPortAdapter.loadRows` in [JdbcImportAuditPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/JdbcImportAuditPortAdapter.java) (line 82) executes the paginated query using `OFFSET` and `FETCH NEXT`.
11. The use case returns `ImportAuditReport` in [ImportAuditReport.java](../src/main/java/com/example/spring_batch_demo/application/customer/dto/ImportAuditReport.java) (line 10).
12. The controller returns `500` if `report.jobStatus()` is `FAILED`; otherwise it returns `200`.

Example response:

```json
{
  "jobExecutionId": 41,
  "jobStatus": "COMPLETED",
  "totalRejectedRows": 5,
  "rows": [
    {
      "category": "PARSE_SKIP",
      "lineNumber": 9,
      "reason": "Incorrect token count",
      "sourceId": null,
      "sourceName": null,
      "sourceEmail": "bad,row,with,too,many,tokens"
    }
  ]
}
```

## Phase 2 response and exception mapping

| Situation | Final response |
|-----------|----------------|
| Missing/blank `inputFile` on POST | `400 ProblemDetail`, handled by `onMissingInputFile` in [BatchJobApiExceptionHandler.java](../src/main/java/com/example/spring_batch_demo/presentation/api/exceptions/BatchJobApiExceptionHandler.java) (line 27) |
| Direct job launch fails | `500 ProblemDetail`, handled by `onImportLaunchFailed` in [BatchJobApiExceptionHandler.java](../src/main/java/com/example/spring_batch_demo/presentation/api/exceptions/BatchJobApiExceptionHandler.java) (line 38) |
| Non-numeric `jobExecutionId` | `400 ProblemDetail`, handled by `onBadPathVariable` in [BatchJobApiExceptionHandler.java](../src/main/java/com/example/spring_batch_demo/presentation/api/exceptions/BatchJobApiExceptionHandler.java) (line 71) |
| Status unknown job id | `404` |
| Status known job with `FAILED` status | `500` with `CustomerImportResult` body |
| Report unknown job id | `404` |
| Report known job with `FAILED` status | `500` with `ImportAuditReport` body |
| Report/status known non-failed job | `200` |

`COMPLETED` with rejected rows is still successful HTTP `200`. Rejected business rows are represented by `skipCount`, `filterCount`, and report rows.

## Phase 2 database objects

Phase 2 adds:

- `IMPORT_REJECTED_ROW`, created in [schema.sql](../src/main/resources/schema.sql) (line 24) and [schema-h2-import-audit-it.sql](../src/main/resources/schema-h2-import-audit-it.sql) (line 7).

Important columns:

- `JOB_EXECUTION_ID`: links audit rows to a Spring Batch execution
- `CATEGORY`: one of the `ImportRejectionCategory` values
- `LINE_NUMBER`: source row number when available
- `REASON`: parse/skip/filter explanation
- `SOURCE_ID`, `SOURCE_NAME`, `SOURCE_EMAIL`: original source values when available

Phase 2 still uses:

- `CUSTOMER`, for accepted rows
- Spring Batch `BATCH_*`, for job state and counters

## Phase 2 happy flow summary

```text
POST import
  -> direct publisher starts job
  -> customerStep reads CSV
  -> processor applies domain policy
  -> invalid email returns null
  -> afterProcess records POLICY_FILTER
  -> parse/process/write skips record skip categories
  -> valid rows are written to CUSTOMER or no-op writer in audit-it
  -> status reads BATCH_* and first 10 audit rows
  -> report reads paginated IMPORT_REJECTED_ROW rows
```
