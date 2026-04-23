---
theme: default
title: Spring Batch CSV to Oracle - current architecture
info: |
  Spring Boot 3.2 + Spring Batch; RabbitMQ command boundary in dev; audit/reporting; H2 smoke profiles.
---

# Spring Batch customer import

CSV input becomes `Customer` rows, valid rows are upserted into `CUSTOMER`, and rejected rows are recorded for audit.

This deck is the high-level map:

- REST entry points and response semantics
- Onion layer boundaries
- RabbitMQ vs in-process launch
- Batch read/process/write flow
- Audit/reporting and DB tables

---

# Current capability snapshot

| Capability | Current behavior |
|------------|------------------|
| Import trigger | `POST /api/batch/customer/import?inputFile=...` |
| Async boundary | RabbitMQ in `dev`; direct in-process launch in `audit-it` / `test` |
| Status | `GET /api/batch/customer/import/{jobExecutionId}/status` |
| Audit report | `GET /api/batch/customer/import/{jobExecutionId}/report?limit=&offset=` |
| Correlation lookup | `GET /api/batch/customer/import/by-correlation/{correlationId}/job` |
| Persistence | Oracle `MERGE` for customers; audit rows in `IMPORT_REJECTED_ROW` |

---

# Onion layers

```mermaid
flowchart LR
  subgraph presentation
    API[BatchJobController]
    EX[BatchJobApiExceptionHandler]
  end

  subgraph application
    UC[CustomerImportUseCase]
    PUB[CustomerImportCommandPublisher]
    CORR[ImportLaunchCorrelationPort]
    AUDIT[ImportAuditPort]
    DTO[DTOs: Command / EnqueueResponse / Result / Report]
  end

  subgraph domain
    C[Customer]
    POLICY[CustomerImportPolicy]
    REJ[RejectedRow + ImportRejectionCategory]
  end

  subgraph infrastructure
    BATCH[Spring Batch adapters + configs]
    JDBC[JDBC adapters]
    MQ[RabbitMQ adapters + config]
  end

  API --> PUB
  API --> UC
  API --> CORR
  API --> DTO
  EX --> API
  BATCH -.->|implements| UC
  MQ -.->|implements| PUB
  JDBC -.->|implements| AUDIT
  JDBC -.->|implements| CORR
  BATCH --> POLICY
  BATCH --> AUDIT
  JDBC --> C
  DTO --> REJ
```

Dependency rule: presentation and infrastructure depend inward on application/domain contracts; domain has no Spring/JDBC/Batch types.

---

# Runtime modes

| Profile | Messaging | Database | Customer writer | Use case |
|---------|-----------|----------|-----------------|----------|
| default | off | Oracle config, no schema init | Oracle MERGE | safer prod-like defaults |
| `dev` | RabbitMQ on | Oracle XE | Oracle MERGE | full Phase 3 path |
| `audit-it` | off | H2 Oracle mode | no-op customer upsert | fast REST + batch + audit smoke |
| `test` | off | H2 Oracle mode | test wiring/mocks | automated tests |
| `amqp-it` | on | H2 Oracle mode | no-op customer upsert | AMQP integration tests |

---

# Endpoint map

| Request | Success | Important failures |
|---------|---------|--------------------|
| `POST /customer/import?inputFile=...` | `202` + `CustomerImportEnqueueResponse` | `400` missing/blank/unreadable input, `500` launch failure, `503` publish failure |
| `GET /customer/import/by-correlation/{uuid}/job` | `200` + `{jobExecutionId}` | `400` invalid UUID, `404` not launched yet |
| `GET /customer/import/{id}/status` | `200` + `CustomerImportResult` | `404` unknown id, `500` if batch `FAILED` |
| `GET /customer/import/{id}/report?limit=&offset=` | `200` + `ImportAuditReport` | `404` unknown id, `500` if batch `FAILED` |

---

# POST in `dev`: RabbitMQ path

```mermaid
sequenceDiagram
autonumber
actor Client
participant API as BatchJobController
participant Val as CustomerImportInputFile
participant Res as InputFileValidator
participant Pub as AmqpCustomerImportCommandPublisher
participant Stage as InputFileStagingPort
participant MQ as RabbitMQ
participant L as CustomerImportJobLaunchListener
participant UC as CustomerImportUseCase
participant Corr as ImportLaunchCorrelationPort

Client->>API: POST /customer/import?inputFile=...
API->>Val: requireInputFileLocation(inputFile)
Val-->>API: trimmed resource location
API->>Res: validateAvailable(inputFile)
API->>Pub: publish(CustomerImportCommand)
Pub->>Stage: stageForImport(inputFile, correlationId)
Stage-->>Pub: staged classpath location or original classpath
Pub->>MQ: JSON command with staged inputFile
Pub-->>API: EnqueueResponse(correlationId, QUEUED, null)
API-->>Client: 202 Accepted
MQ-->>L: deliver command
L->>UC: launchImport(staged inputFile)
UC-->>L: jobExecutionId
L->>Corr: registerLaunchedJob(correlationId, jobExecutionId)
```

---

# POST in `audit-it`: direct path

```mermaid
sequenceDiagram
autonumber
actor Client
participant API as BatchJobController
participant Val as CustomerImportInputFile
participant Res as InputFileValidator
participant Pub as DirectCustomerImportCommandPublisher
participant Stage as InputFileStagingPort
participant UC as CustomerImportUseCase
participant Corr as ImportLaunchCorrelationPort

Client->>API: POST /customer/import?inputFile=...
API->>Val: requireInputFileLocation(inputFile)
API->>Res: validateAvailable(inputFile)
API->>Pub: publish(command)
Pub->>Stage: stageForImport(inputFile, correlationId)
Stage-->>Pub: staged classpath location or original classpath
Pub->>UC: launchImport(staged inputFile)
UC-->>Pub: jobExecutionId
Pub->>Corr: registerLaunchedJob(correlationId, jobExecutionId)
Pub-->>API: EnqueueResponse(correlationId, STARTED, jobExecutionId)
API-->>Client: 202 Accepted
```

---

# Batch execution

```mermaid
flowchart TD
  A[customerJob] --> B[customerStep chunk size 10]
  B --> C[customerReader reads CSV resource]
  C --> D[Customer record]
  D --> E[CustomerItemProcessorAdapter]
  E --> F[EmailAndNameCustomerImportPolicy]
  F -->|valid email| G[uppercase name]
  F -->|invalid email| H[filter row + POLICY_FILTER audit]
  G --> I[CustomerUpsertItemWriterAdapter]
  I --> J[CustomerUpsertPort]
  J --> K[OracleCustomerUpsertPortAdapter]
  K --> L[(CUSTOMER via MERGE)]
  C -->|parse exception| M[skip + PARSE_SKIP audit]
```

---

# Fault tolerance and audit hooks

| Event | Spring Batch behavior | Audit behavior |
|-------|-----------------------|----------------|
| Invalid email | processor returns `null`, increments `filterCount` | `POLICY_FILTER` row |
| Malformed CSV | skippable read exception, increments `skipCount` | `PARSE_SKIP` row |
| Process skip | skippable processor exception | `PROCESS_SKIPPED` row |
| Write skip | skippable writer exception | `WRITE_SKIPPED` row |
| Transient DB issue | retry up to 3 times with exponential backoff | no audit unless ultimately skipped/failed |

---

# Database model

```mermaid
erDiagram
  CUSTOMER {
    NUMBER ID PK
    VARCHAR2 NAME
    VARCHAR2 EMAIL
  }

  IMPORT_REJECTED_ROW {
    NUMBER ID PK
    NUMBER JOB_EXECUTION_ID
    VARCHAR2 CATEGORY
    NUMBER LINE_NUMBER
    VARCHAR2 REASON
    VARCHAR2 SOURCE_ID
    VARCHAR2 SOURCE_NAME
    VARCHAR2 SOURCE_EMAIL
  }

  IMPORT_LAUNCH_CORRELATION {
    VARCHAR2 CORRELATION_ID PK
    NUMBER JOB_EXECUTION_ID
    TIMESTAMP CREATED_AT
  }

  BATCH_JOB_EXECUTION {
    NUMBER JOB_EXECUTION_ID PK
    VARCHAR2 STATUS
  }

  BATCH_JOB_EXECUTION ||--o{ IMPORT_REJECTED_ROW : audit
  BATCH_JOB_EXECUTION ||--o| IMPORT_LAUNCH_CORRELATION : correlation
```

---

# Status vs report

`GET .../status` is the quick operational view:

- `status`
- `failures`
- `readCount`, `writeCount`, `skipCount`, `filterCount`
- no row-level audit sample; use `GET .../report` for persisted rejected rows

`GET .../report` is the detailed row-level view:

- `jobStatus`
- `totalRejectedRows`
- paginated `RejectedRow` list

---

# Response mapping

```mermaid
flowchart TD
  A[Client calls status or report] --> B[JobExplorer lookup]
  B -->|missing id| C[404 Not Found]
  B -->|found| D[Build DTO from Batch metadata + audit port]
  D --> E{Batch status FAILED?}
  E -->|yes| F[500 with JSON body]
  E -->|no| G[200 with JSON body]
```

The `500` body is still useful: clients can parse counts and audit rows even when the job failed.

---

# Run paths

```bash
# Full dev path: Oracle + RabbitMQ required
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Fast local smoke: H2 + no RabbitMQ + no Oracle MERGE
./mvnw spring-boot:run -Dspring-boot.run.profiles=audit-it

# Test suite
./mvnw clean verify
```

---

# Deck guide

| Deck | Use when |
|------|----------|
| `slides.md` | you need the whole system in one pass |
| `slides-phase2.md` | audit/reporting design deep dive |
| `slides-phase2-flows.md` | status/report HTTP behavior only |
| `slides-phase3.md` | RabbitMQ command boundary |
| `flows.md` | method-call and runtime-flow walkthrough |
