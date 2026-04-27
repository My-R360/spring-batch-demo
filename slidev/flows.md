---
theme: default
title: Spring Batch flow walkthrough
info: |
  Method-call and runtime-flow deck for POST import, correlation lookup, status, report, batch execution, and DB writes.
---

# Spring Batch flow walkthrough

This is the method-call view of the current codebase.

It follows each request from client to controller, application ports, infrastructure adapters, Spring Batch, and database.

---

# One-page runtime map

```mermaid
flowchart LR
  Client[Client] --> API[BatchJobController]
  API --> Publisher[CustomerImportCommandPublisher]
  Publisher -->|dev| Rabbit[RabbitMQ]
  Publisher -->|audit-it/test| Direct[Direct launch]
  Rabbit --> Listener[CustomerImportJobLaunchListener]
  Listener --> UC[CustomerImportUseCase]
  Direct --> UC
  UC --> Launcher[async JobLauncher]
  Launcher --> Job[customerJob/customerStep]
  Job --> Reader[CSV Reader]
  Job --> Processor[Policy Processor]
  Job --> Writer[Writer]
  Writer --> CustomerDB[(CUSTOMER)]
  Job --> Audit[(IMPORT_REJECTED_ROW)]
  Listener --> Corr[(IMPORT_LAUNCH_CORRELATION)]
```

---

# Current endpoints

| Request | Controller method | Response shape |
|---------|-------------------|----------------|
| `POST /api/batch/customer/import?inputFile=...` | `importCustomers` | `CustomerImportEnqueueResponse` |
| `GET /api/batch/customer/import/by-correlation/{uuid}/job` | `getJobExecutionIdByCorrelation` | `{jobExecutionId}` |
| `GET /api/batch/customer/import/{id}/status` | `getImportStatus` | `CustomerImportResult` |
| `GET /api/batch/customer/import/{id}/report` | `getImportAuditReport` | `ImportAuditReport` |

---

# POST import - controller chain

```mermaid
sequenceDiagram
autonumber
actor Client
participant C as BatchJobController.importCustomers
participant V as CustomerImportInputFile
participant Cmd as CustomerImportCommand
participant P as CustomerImportCommandPublisher

Client->>C: POST ?inputFile=...
C->>V: requireInputFileLocation(inputFile)
V-->>C: trimmed resource location
C->>C: UUID.randomUUID()
C->>Cmd: of(correlationId, resolvedInput)
C->>P: publish(command)
P-->>C: CustomerImportEnqueueResponse
C-->>Client: 202 Accepted
```

---

# Publisher branch detail

```mermaid
flowchart TD
  A[CustomerImportCommandPublisher.publish] --> B{app.messaging.customer-import.enabled}
  B -->|true| C[AmqpCustomerImportCommandPublisher]
  C --> D[RabbitTemplate.convertAndSend]
  D --> E[return QUEUED, jobExecutionId null]
  B -->|false| F[DirectCustomerImportCommandPublisher]
  F --> G[CustomerImportUseCase.launchImport]
  G --> H[registerLaunchedJob]
  H --> I[return STARTED, jobExecutionId present]
```

---

# RabbitMQ listener chain

```mermaid
sequenceDiagram
autonumber
participant MQ as RabbitMQ queue
participant L as CustomerImportJobLaunchListener
participant UC as SpringBatchCustomerImportUseCase
participant Corr as ImportLaunchCorrelationPort
participant DB as IMPORT_LAUNCH_CORRELATION

MQ-->>L: CustomerImportCommand with staged inputFile
L->>UC: launchImport(staged inputFile)
UC-->>L: jobExecutionId
L->>Corr: registerLaunchedJob(correlationId, jobExecutionId)
Corr->>DB: INSERT IF ABSENT
L-->>MQ: return successfully, AUTO ack
```

---

# Batch launch chain

```mermaid
sequenceDiagram
autonumber
participant UC as SpringBatchCustomerImportUseCase
participant V as CustomerImportInputFile
participant JL as asyncJobLauncher
participant Job as customerJob

UC->>V: requireInputFileLocation(inputFile)
V-->>UC: trimmed inputFile
UC->>UC: build JobParameters(inputFile, run.at)
UC->>JL: run(customerJob, params)
JL-->>UC: JobExecution(id, STARTING)
JL-->>Job: execute asynchronously
```

---

# Batch read/process/write chain

```mermaid
sequenceDiagram
autonumber
participant Job as customerJob
participant Step as customerStep
participant Reader as customerReader
participant Processor as CustomerItemProcessorAdapter
participant Policy as EmailAndNameCustomerImportPolicy
participant Writer as CustomerUpsertItemWriterAdapter
participant DB as OracleCustomerUpsertPortAdapter

Job->>Step: start
Step->>Reader: read CSV row
Reader-->>Step: Customer
Step->>Processor: process(customer)
Processor->>Policy: apply(customer)
alt valid email
  Policy-->>Processor: uppercased Customer
  Processor-->>Step: Customer
  Step->>Writer: write(chunk)
  Writer->>DB: upsert(customers)
  DB-->>Writer: MERGE success
else invalid email
  Policy-->>Processor: null
  Processor-->>Step: null
  Step->>Step: increment filterCount
end
```

---

# Audit branch inside batch

```mermaid
flowchart TD
  A[customerStep] --> B{row path}
  B -->|parse exception| C[onSkipInRead]
  C --> D[RejectedRow PARSE_SKIP]
  B -->|processor returns null| E[afterProcess]
  E --> F[RejectedRow POLICY_FILTER]
  B -->|process exception| G[onSkipInProcess]
  G --> H[RejectedRow PROCESS_SKIPPED]
  B -->|write exception| I[onSkipInWrite]
  I --> J[RejectedRow WRITE_SKIPPED]
  D --> K[ImportAuditPort.recordRejected]
  F --> K
  H --> K
  J --> K
  K --> L[(IMPORT_REJECTED_ROW)]
```

---

# Correlation lookup flow

```mermaid
sequenceDiagram
autonumber
actor Client
participant C as BatchJobController.getJobExecutionIdByCorrelation
participant Port as ImportLaunchCorrelationPort
participant DB as IMPORT_LAUNCH_CORRELATION

Client->>C: GET /by-correlation/{correlationId}/job
C->>C: validate UUID
C->>Port: findJobExecutionId(correlationId)
Port->>DB: SELECT JOB_EXECUTION_ID
alt missing
  DB-->>Port: no row
  Port-->>C: OptionalLong.empty
  C-->>Client: 404
else found
  DB-->>Port: id
  Port-->>C: OptionalLong.of(id)
  C-->>Client: 200 {"jobExecutionId": id}
end
```

---

# Status flow

```mermaid
sequenceDiagram
autonumber
actor Client
participant C as BatchJobController.getImportStatus
participant UC as SpringBatchCustomerImportUseCase
participant JE as JobExplorer

Client->>C: GET /customer/import/{id}/status
C->>UC: getImportStatus(id)
UC->>JE: getJobExecution(id)
alt not found
  JE-->>UC: null
  UC-->>C: null
  C-->>Client: 404
else found
  JE-->>UC: JobExecution
  UC->>UC: resolve failures + aggregate counts
  UC-->>C: CustomerImportResult
  C-->>Client: 200 or 500
end
```

---

# Report flow

```mermaid
sequenceDiagram
autonumber
actor Client
participant C as BatchJobController.getImportAuditReport
participant UC as SpringBatchCustomerImportUseCase
participant JE as JobExplorer
participant Audit as ImportAuditPort

Client->>C: GET /customer/import/{id}/report?limit=&offset=
C->>UC: getImportAuditReport(id, limit, offset)
UC->>JE: getJobExecution(id)
alt not found
  JE-->>UC: null
  UC-->>C: null
  C-->>Client: 404
else found
  JE-->>UC: JobExecution
  UC->>Audit: countRejected(id)
  UC->>Audit: loadRows(id, safeLimit, safeOffset)
  Audit-->>UC: rows
  UC-->>C: ImportAuditReport
  C-->>Client: 200 or 500
end
```

---

# DB tables by flow

| Flow | Table touched | Why |
|------|---------------|-----|
| batch metadata | `BATCH_*` | Spring Batch job/step state |
| customer writer | `CUSTOMER` | upsert valid customers |
| audit listener | `IMPORT_REJECTED_ROW` | persist rejected rows |
| correlation registration | `IMPORT_LAUNCH_CORRELATION` | map HTTP correlation id to job id |
| status/report | `BATCH_*`, `IMPORT_REJECTED_ROW` | read progress and audit |

---

# Response matrix

| Scenario | HTTP |
|----------|------|
| POST accepted by RabbitMQ | `202 QUEUED` |
| POST accepted by direct launcher | `202 STARTED` |
| POST missing `inputFile` | `400 ProblemDetail` |
| Rabbit publish fails | `500 ProblemDetail` |
| correlation id invalid | `400 ProblemDetail` |
| correlation not launched yet | `404` |
| status/report unknown job | `404` |
| status/report failed job | `500` with JSON body |
| status/report non-failed job | `200` with JSON body |

---

# Job lifecycle

```mermaid
stateDiagram-v2
  [*] --> QUEUED: RabbitMQ path
  QUEUED --> STARTING: listener calls launchImport
  [*] --> STARTING: direct path
  STARTING --> STARTED
  STARTED --> COMPLETED
  STARTED --> FAILED
  STARTED --> STOPPED
  FAILED --> [*]
  COMPLETED --> [*]
  STOPPED --> [*]
```

---

# Method chain summary

POST:

`BatchJobController.importCustomers` -> `CustomerImportInputFile` -> `CustomerImportCommandPublisher` -> AMQP or direct publisher -> `CustomerImportUseCase.launchImport`

Batch:

`customerJob` -> `customerStep` -> reader -> processor -> policy -> writer -> DB + audit listener

Polling:

controller -> use case -> `JobExplorer` + audit port -> DTO -> HTTP response mapping
