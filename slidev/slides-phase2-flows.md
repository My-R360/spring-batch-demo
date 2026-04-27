---
theme: default
title: Spring Batch Demo - Phase 2 HTTP Flows
info: |
  Flow-only companion for Phase 2 request/response branches, status/report behavior, and audit payload timing.
---

# Phase 2 HTTP flows

This deck answers:

- what request is sent
- which controller method is hit
- what downstream method is called
- what response is returned
- when audit rows become visible

---

# Endpoint inventory

| Endpoint | Method | Controller method | Main output |
|----------|--------|-------------------|-------------|
| `/api/batch/customer/import` | `POST` | `importCustomers` | `CustomerImportEnqueueResponse` |
| `/api/batch/customer/import/by-correlation/{id}/job` | `GET` | `getJobExecutionIdByCorrelation` | `{jobExecutionId}` |
| `/api/batch/customer/import/{id}/status` | `GET` | `getImportStatus` | `CustomerImportResult` |
| `/api/batch/customer/import/{id}/report` | `GET` | `getImportAuditReport` | `ImportAuditReport` |

---

# POST flow - common start

```mermaid
sequenceDiagram
autonumber
actor Client
participant API as BatchJobController.importCustomers
participant In as CustomerImportInputFile
participant Pub as CustomerImportCommandPublisher

Client->>API: POST ?inputFile=...
API->>In: requireInputFileLocation(inputFile)
In-->>API: trimmed inputFile
API->>API: UUID.randomUUID()
API->>API: CustomerImportCommand.of(correlationId, inputFile)
API->>Pub: publish(command)
Pub-->>API: CustomerImportEnqueueResponse
API-->>Client: 202 Accepted
```

The concrete publisher depends on profile/config.

---

# Publisher branch

```mermaid
flowchart LR
  A[CustomerImportCommandPublisher] --> B{messaging enabled?}
  B -->|true, dev| C[AmqpCustomerImportCommandPublisher]
  C --> D[RabbitTemplate.convertAndSend]
  D --> E[202 QUEUED + correlationId]
  B -->|false, audit-it/test| F[DirectCustomerImportCommandPublisher]
  F --> G[CustomerImportUseCase.launchImport]
  G --> H[register correlation]
  H --> I[202 STARTED + jobExecutionId]
```

---

# POST response bodies

RabbitMQ path:

```json
{
  "correlationId": "c6b4...",
  "status": "QUEUED",
  "jobExecutionId": null
}
```

Direct path:

```json
{
  "correlationId": "c6b4...",
  "status": "STARTED",
  "jobExecutionId": 42
}
```

---

# POST failures

```mermaid
flowchart TD
  A[POST /customer/import] --> B{inputFile null or blank?}
  B -->|yes| C[MissingInputFileException]
  C --> D[400 ProblemDetail: Missing input file]
  B -->|no| E[publish command]
  E -->|Rabbit publish fails| F[ImportCommandPublishException]
  F --> G[500 ProblemDetail: Import command publish failed]
  E -->|Direct launch fails| H[ImportJobLaunchException]
  H --> I[500 ProblemDetail: Import job launch failed]
  E -->|ok| J[202 Accepted]
```

---

# Correlation lookup

```mermaid
sequenceDiagram
autonumber
actor Client
participant API as BatchJobController.getJobExecutionIdByCorrelation
participant Corr as ImportLaunchCorrelationPort
participant DB as IMPORT_LAUNCH_CORRELATION

Client->>API: GET /by-correlation/{uuid}/job
API->>API: UUID validation
API->>Corr: findJobExecutionId(uuid)
Corr->>DB: SELECT JOB_EXECUTION_ID
alt not launched yet
  DB-->>Corr: none
  Corr-->>API: OptionalLong.empty()
  API-->>Client: 404
else mapping exists
  DB-->>Corr: jobExecutionId
  Corr-->>API: OptionalLong.of(id)
  API-->>Client: 200 {"jobExecutionId": id}
end
```

---

# Status flow

```mermaid
sequenceDiagram
autonumber
actor Client
participant API as BatchJobController.getImportStatus
participant UC as SpringBatchCustomerImportUseCase
participant JE as JobExplorer
participant Audit as ImportAuditPort

Client->>API: GET /customer/import/{jobExecutionId}/status
API->>UC: getImportStatus(id)
UC->>JE: getJobExecution(id)
JE-->>UC: JobExecution or null
alt missing
  UC-->>API: null
  API-->>Client: 404
else found
  UC->>UC: aggregate step counts
  UC->>UC: resolve persisted failure messages
  UC-->>API: CustomerImportResult
  API-->>Client: 200 or 500
end
```

---

# Status response mapping

```mermaid
flowchart TD
  A[CustomerImportResult] --> B{result null?}
  B -->|yes| C[404 Not Found]
  B -->|no| D{status FAILED?}
  D -->|yes| E[500 + CustomerImportResult]
  D -->|no| F[200 + CustomerImportResult]
```

---

# Report flow

```mermaid
sequenceDiagram
autonumber
actor Client
participant API as BatchJobController.getImportAuditReport
participant UC as SpringBatchCustomerImportUseCase
participant JE as JobExplorer
participant Audit as ImportAuditPort

Client->>API: GET /customer/import/{id}/report?limit=50&offset=0
API->>UC: getImportAuditReport(id, limit, offset)
UC->>JE: getJobExecution(id)
JE-->>UC: JobExecution or null
alt missing
  UC-->>API: null
  API-->>Client: 404
else found
  UC->>UC: clamp limit to 1..500
  UC->>UC: normalize offset >= 0
  UC->>Audit: countRejected(id)
  Audit-->>UC: totalRejectedRows
  UC->>Audit: loadRows(id, limit, offset)
  Audit-->>UC: rows
  UC-->>API: ImportAuditReport
  API-->>Client: 200 or 500
end
```

---

# Report response mapping

```mermaid
flowchart TD
  A[ImportAuditReport] --> B{report null?}
  B -->|yes| C[404 Not Found]
  B -->|no| D{jobStatus FAILED?}
  D -->|yes| E[500 + ImportAuditReport]
  D -->|no| F[200 + ImportAuditReport]
```

---

# Audit timing

```mermaid
timeline
    title When audit becomes visible
    POST accepted : returns 202 before job finishes
    Reader parse skip : listener writes PARSE_SKIP
    Domain policy filter : listener writes POLICY_FILTER
    Status poll : reads first 10 audit rows
    Report poll : reads paginated audit rows
    Job failed : status/report return 500 with body
```

---

# Flow table

| Scenario | Final response |
|----------|----------------|
| POST valid, Rabbit path | `202 QUEUED`, no `jobExecutionId` yet |
| POST valid, direct path | `202 STARTED`, includes `jobExecutionId` |
| POST missing input | `400 ProblemDetail` |
| correlation not registered yet | `404` |
| status/report unknown job id | `404` |
| status/report failed batch job | `500` with JSON body |
| status/report running or completed | `200` with JSON body |

---

# Curl chain

```bash
# 1. Accept work
curl -s -X POST \
  "http://localhost:8080/api/batch/customer/import?inputFile=classpath:customers-phase2-audit-sample.csv"

# 2. If POST returned QUEUED, resolve job id
curl -s "http://localhost:8080/api/batch/customer/import/by-correlation/<uuid>/job"

# 3. Poll status
curl -s "http://localhost:8080/api/batch/customer/import/<jobExecutionId>/status" | jq .

# 4. Fetch full report
curl -s "http://localhost:8080/api/batch/customer/import/<jobExecutionId>/report?limit=20&offset=0" | jq .
```

---

# Read this deck with code

| Slide concept | Code anchor |
|---------------|-------------|
| POST flow | `BatchJobController.importCustomers` |
| direct vs Rabbit branch | `DirectCustomerImportCommandPublisher`, `AmqpCustomerImportCommandPublisher` |
| status/report build | `SpringBatchCustomerImportUseCase` |
| audit insert/read | `JdbcImportAuditPortAdapter` |
| row categories | `ImportRejectionCategory`, `RejectedRow` |
