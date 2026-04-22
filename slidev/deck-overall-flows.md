---
theme: default
title: Spring Batch Demo - Overall Flows
info: |
  Complete flow deck combining endpoints, phase flows, data, database tables, exceptions, and method chains.
---

# Overall flows

This deck ties all phases together:

- onion architecture boundaries
- all HTTP endpoints
- direct and RabbitMQ POST branches
- batch read/process/write flow
- audit/report flow
- correlation lookup flow
- DB tables and response rules
- where the successful flow goes next

---

# One-page runtime map

```mermaid
flowchart LR
  Client[Client] --> API[BatchJobController]
  API --> Pub[CustomerImportCommandPublisher]
  Pub -->|messaging disabled| Direct[Direct publisher]
  Pub -->|messaging enabled| Amqp[AMQP publisher]
  Amqp --> Rabbit[(RabbitMQ)]
  Rabbit --> Listener[Job launch listener]
  Direct --> UseCase[CustomerImportUseCase]
  Listener --> UseCase
  UseCase --> Batch[Spring Batch customerJob]
  Batch --> Reader[CSV reader]
  Reader --> Processor[Domain policy]
  Processor --> Writer[Customer writer]
  Writer --> CustomerDB[(CUSTOMER)]
  Batch --> BatchDB[(BATCH_* metadata)]
  Batch --> Audit[(IMPORT_REJECTED_ROW)]
  Listener --> Corr[(IMPORT_LAUNCH_CORRELATION)]
  API --> Status[status/report/correlation reads]
  Status --> BatchDB
  Status --> Audit
  Status --> Corr
```

---

# Endpoint inventory

| Endpoint | Purpose | Success |
|----------|---------|---------|
| `POST /api/batch/customer/import?inputFile=...` | accept import request | `202` with `CustomerImportEnqueueResponse` |
| `GET /api/batch/customer/import/by-correlation/{correlationId}/job` | resolve queued request to `jobExecutionId` | `200` with `{"jobExecutionId": ...}` |
| `GET /api/batch/customer/import/{jobExecutionId}/status` | read batch progress and rejected sample | `200` with `CustomerImportResult` |
| `GET /api/batch/customer/import/{jobExecutionId}/report?limit=&offset=` | read paginated rejected rows | `200` with `ImportAuditReport` |

---

# POST branch selector

```mermaid
flowchart TD
  A[POST import] --> B[validate inputFile]
  B --> C[create correlationId]
  C --> D[CustomerImportCommandPublisher.publish]
  D --> E{app.messaging.customer-import.enabled?}
  E -->|false| F[DirectCustomerImportCommandPublisher]
  E -->|true| G[AmqpCustomerImportCommandPublisher]
  F --> H[launchImport immediately]
  H --> I[register correlation]
  I --> J[202 STARTED + jobExecutionId]
  G --> K[publish to RabbitMQ]
  K --> L[202 QUEUED + null jobExecutionId]
```

The client decides next action from `status` and `jobExecutionId`.

---

# POST responses

Direct mode:

```json
{
  "correlationId": "2f8f4f22-4c87-48e4-9de9-e53c4f4fe19d",
  "status": "STARTED",
  "jobExecutionId": 41
}
```

RabbitMQ mode:

```json
{
  "correlationId": "2f8f4f22-4c87-48e4-9de9-e53c4f4fe19d",
  "status": "QUEUED",
  "jobExecutionId": null
}
```

---

# RabbitMQ listener chain

```mermaid
sequenceDiagram
autonumber
participant MQ as customer.import.queue
participant Listener as CustomerImportJobLaunchListener
participant UC as SpringBatchCustomerImportUseCase
participant Corr as ImportLaunchCorrelationPort
participant DB as IMPORT_LAUNCH_CORRELATION

MQ-->>Listener: CustomerImportCommand with staged inputFile
Listener->>UC: launchImport(staged inputFile)
UC-->>Listener: jobExecutionId
Listener->>Corr: registerLaunchedJob(correlationId, jobExecutionId)
Corr->>DB: INSERT if absent
Listener-->>MQ: return successfully, AUTO ack
```

If listener throws repeatedly, retry exhausts and RabbitMQ dead-letters the message.

---

# Batch launch method chain

```mermaid
flowchart LR
  A[launchImport(inputFile)] --> B[CustomerImportInputFile.requireInputFileLocation]
  B --> C[InputFileStagingPort fallback]
  C --> D[InputFileValidator]
  D --> E[JobParametersBuilder]
  E --> F[inputFile parameter]
  E --> G[run.at timestamp]
  F --> H[asyncJobLauncher.run]
  G --> H
  H --> I[customerJob]
  I --> J[customerStep]
```

The use case catches launch exceptions and wraps them in `ImportJobLaunchException`.

---

# Batch read/process/write chain

```mermaid
flowchart TD
  A[customerStep chunk size 10] --> B[customerReader]
  B --> C[CSV row id,name,email]
  C --> D[CustomerItemProcessorAdapter]
  D --> E[EmailAndNameCustomerImportPolicy]
  E --> F{valid email?}
  F -->|no| G[return null]
  G --> H[filterCount + POLICY_FILTER audit]
  F -->|yes| I[uppercase name]
  I --> J[CustomerUpsertItemWriterAdapter]
  J --> K[CustomerUpsertPort]
  K --> L[Oracle MERGE CUSTOMER]
```

The domain policy is the business decision point.

---

# Audit branch inside batch

```mermaid
flowchart TD
  A[Batch lifecycle event] --> B{event type}
  B -->|read parse skip| C[PARSE_SKIP]
  B -->|other read skip| D[READ_SKIPPED]
  B -->|process skip| E[PROCESS_SKIPPED]
  B -->|write skip| F[WRITE_SKIPPED]
  B -->|processor returned null| G[POLICY_FILTER]
  C --> H[RejectedRow]
  D --> H
  E --> H
  F --> H
  G --> H
  H --> I[ImportAuditPort.recordRejected]
  I --> J[JdbcImportAuditPortAdapter]
  J --> K[(IMPORT_REJECTED_ROW)]
```

Audit is written by infrastructure through an application port.

---

# Correlation lookup flow

```mermaid
flowchart TD
  A[GET by-correlation/{correlationId}/job] --> B{valid UUID?}
  B -->|no| C[400 ProblemDetail]
  B -->|yes| D[ImportLaunchCorrelationPort.findJobExecutionId]
  D --> E{row exists?}
  E -->|no| F[404 no body]
  E -->|yes| G[200 {jobExecutionId}]
```

In RabbitMQ mode, poll this endpoint until the listener has launched the job.

---

# Status flow

```mermaid
flowchart TD
  A[GET /import/{jobExecutionId}/status] --> B{jobExecutionId numeric?}
  B -->|no| C[400 ProblemDetail]
  B -->|yes| D[JobExplorer.getJobExecution]
  D --> E{execution exists?}
  E -->|no| F[404]
  E -->|yes| G[sum step counts]
  G --> H[load first 10 rejected audit rows]
  H --> I{status FAILED?}
  I -->|yes| J[500 CustomerImportResult]
  I -->|no| K[200 CustomerImportResult]
```

Failure status returns a structured body instead of hiding counts.

---

# Report flow

```mermaid
flowchart TD
  A[GET /import/{jobExecutionId}/report?limit=&offset=] --> B[JobExplorer.getJobExecution]
  B --> C{execution exists?}
  C -->|no| D[404]
  C -->|yes| E[clamp limit 1..500]
  E --> F[offset max 0]
  F --> G[countRejected]
  G --> H[loadRows ordered by ID]
  H --> I{job status FAILED?}
  I -->|yes| J[500 ImportAuditReport]
  I -->|no| K[200 ImportAuditReport]
```

Report is the full paginated audit surface.

---

# Database tables by flow

| Table | Written by | Read by | Purpose |
|-------|------------|---------|---------|
| `CUSTOMER` | customer writer / Oracle adapter | external verification | accepted customer rows |
| `BATCH_*` | Spring Batch | `JobExplorer`, Spring Batch | execution state, counts, exit descriptions |
| `IMPORT_REJECTED_ROW` | audit listener through JDBC adapter | status/report | rejected row detail |
| `IMPORT_LAUNCH_CORRELATION` | direct publisher or RabbitMQ listener | correlation endpoint | `correlationId -> jobExecutionId` |

---

# Combined ER model

```mermaid
erDiagram
  CUSTOMER {
    NUMBER ID PK
    VARCHAR2 NAME
    VARCHAR2 EMAIL
  }

  BATCH_JOB_EXECUTION {
    NUMBER JOB_EXECUTION_ID PK
    VARCHAR2 STATUS
    VARCHAR2 EXIT_CODE
  }

  BATCH_STEP_EXECUTION {
    NUMBER STEP_EXECUTION_ID PK
    NUMBER JOB_EXECUTION_ID
    NUMBER READ_COUNT
    NUMBER WRITE_COUNT
    NUMBER FILTER_COUNT
    NUMBER SKIP_COUNT
  }

  IMPORT_REJECTED_ROW {
    NUMBER ID PK
    NUMBER JOB_EXECUTION_ID
    VARCHAR2 CATEGORY
    NUMBER LINE_NUMBER
    VARCHAR2 REASON
  }

  IMPORT_LAUNCH_CORRELATION {
    VARCHAR2 CORRELATION_ID PK
    NUMBER JOB_EXECUTION_ID
    TIMESTAMP CREATED_AT
  }

  BATCH_JOB_EXECUTION ||--o{ BATCH_STEP_EXECUTION : has
  BATCH_JOB_EXECUTION ||--o{ IMPORT_REJECTED_ROW : has
  BATCH_JOB_EXECUTION ||--o| IMPORT_LAUNCH_CORRELATION : maps
```

---

# Response matrix

| Flow | Success | Client next step |
|------|---------|------------------|
| POST direct | `202 STARTED`, `jobExecutionId` present | poll status/report by job id |
| POST RabbitMQ | `202 QUEUED`, `jobExecutionId=null` | poll correlation endpoint |
| correlation lookup found | `200 {"jobExecutionId": ...}` | poll status/report |
| correlation lookup missing | `404` | retry later or inspect listener/queue |
| status running | `200 STARTING/STARTED` | continue polling |
| status complete | `200 COMPLETED` | fetch report if rejected rows matter |
| status failed | `500 FAILED` with result | inspect `failures`, logs, metadata |
| report failed job | `500` with report | audit may still contain useful rows |

---

# Exception map

| Exception / condition | Mapper | HTTP |
|-----------------------|--------|------|
| `MissingInputFileException` | `BatchJobApiExceptionHandler` | `400` |
| `InvalidCorrelationIdException` | `BatchJobApiExceptionHandler` | `400` |
| `MethodArgumentTypeMismatchException` for job id | `BatchJobApiExceptionHandler` | `400` |
| `ImportJobLaunchException` | `BatchJobApiExceptionHandler` | `500` |
| `ImportCommandPublishException` | `BatchJobApiExceptionHandler` | `503` |
| unknown job/correlation | controller branch | `404` |
| batch status `FAILED` | controller branch | `500` with domain DTO body |
| unexpected exception | handler fallback | `500` |

---

# Job lifecycle

```mermaid
stateDiagram-v2
  [*] --> POST_ACCEPTED
  POST_ACCEPTED --> QUEUED: RabbitMQ enabled
  POST_ACCEPTED --> STARTED: direct mode
  QUEUED --> STARTED: listener launches job
  STARTED --> READING
  READING --> PROCESSING
  PROCESSING --> WRITING
  WRITING --> COMPLETED
  READING --> FAILED: parse/skip limit/resource failure
  WRITING --> RETRYING: transient DB failure
  RETRYING --> WRITING
  RETRYING --> FAILED: retry exhausted
  COMPLETED --> STATUS_200
  FAILED --> STATUS_500
```

POST success and job success are intentionally separate concepts.

---

# Phase-to-feature summary

| Phase | Main value | Main files |
|-------|------------|------------|
| Onion | maintain dependency boundaries | domain/application/presentation/infrastructure packages |
| Phase 1 | import CSV into `CUSTOMER` through Spring Batch | reader, processor, writer, use case, job config |
| Phase 2 | persist and expose rejected rows | audit listener, audit port, JDBC audit adapter, report DTO |
| Phase 3 | durable command boundary and correlation | AMQP publisher, RabbitMQ config, listener, correlation adapter |

---

# Successful flow answer

When the selected method is the happy path:

1. `POST` hits `BatchJobController.importCustomers`.
2. input is validated and a `correlationId` is created.
3. publisher branch is chosen by profile.
4. direct branch immediately launches Spring Batch; RabbitMQ branch queues first, then listener launches.
5. `customerJob` executes `customerStep`.
6. CSV rows are read, policy-processed, written, filtered, skipped, and audited.
7. accepted rows go to `CUSTOMER`.
8. job state goes to `BATCH_*`.
9. rejected row details go to `IMPORT_REJECTED_ROW`.
10. queued command mapping goes to `IMPORT_LAUNCH_CORRELATION`.
11. client uses correlation, status, and report endpoints to observe final outcome.
