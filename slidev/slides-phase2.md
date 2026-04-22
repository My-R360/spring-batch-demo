---
theme: default
title: Spring Batch Demo - Phase 2 Audit & Reporting
info: |
  Deep dive on rejected-row audit, status/report APIs, listener hooks, schema, transactions, and smoke verification.
---

# Phase 2 - audit & reporting

Phase 1 told us how many rows were read, written, skipped, or failed.

Phase 2 answers the next operational question:

**Which rows were rejected, and why?**

---

# Phase 2 outcome

| Area | Added in Phase 2 |
|------|------------------|
| Domain | `RejectedRow`, `ImportRejectionCategory` |
| Application | `ImportAuditPort`, `ImportAuditReport`, `filterCount`, `rejectedSample` |
| Infrastructure | `CustomerImportAuditStepListener`, `JdbcImportAuditPortAdapter` |
| Batch config | skip/process listeners wired to `customerStep` |
| DB | `IMPORT_REJECTED_ROW` |
| API | `GET /api/batch/customer/import/{id}/report` |
| Smoke | `audit-it` H2 profile |

---

# Rejection categories

| Category | Captured by | Why it matters |
|----------|-------------|----------------|
| `PARSE_SKIP` | `onSkipInRead` for `FlatFileParseException` | CSV line could not become `Customer` |
| `READ_SKIPPED` | `onSkipInRead` for other read exceptions | reader-level skip |
| `PROCESS_SKIPPED` | `onSkipInProcess` | processor exception |
| `WRITE_SKIPPED` | `onSkipInWrite` | write/upsert exception |
| `POLICY_FILTER` | `afterProcess(input, null)` | business rule rejected row |

`POLICY_FILTER` is a filter, not a Spring Batch skip.

---

# Chunk loop with audit hooks

```mermaid
flowchart TD
  A[Read CSV line] --> B{Read ok?}
  B -->|no, skippable| C[SkipListener.onSkipInRead]
  C --> C1[recordRejected PARSE_SKIP or READ_SKIPPED]
  B -->|yes| D[Customer]
  D --> E[ItemProcessor]
  E --> F{Processor result}
  F -->|Customer| G[Add to chunk]
  F -->|null| H[ItemProcessListener.afterProcess]
  H --> H1[recordRejected POLICY_FILTER]
  E -->|skippable exception| I[SkipListener.onSkipInProcess]
  I --> I1[recordRejected PROCESS_SKIPPED]
  G --> J[ItemWriter]
  J -->|write ok| K[(CUSTOMER)]
  J -->|skippable exception| L[SkipListener.onSkipInWrite]
  L --> L1[recordRejected WRITE_SKIPPED]
```

---

# Listener sequence

```mermaid
sequenceDiagram
autonumber
participant Step as customerStep
participant Reader as FlatFileItemReader
participant Processor as CustomerItemProcessorAdapter
participant Listener as CustomerImportAuditStepListener
participant Port as ImportAuditPort
participant DB as IMPORT_REJECTED_ROW

Step->>Reader: read()
alt parse error
  Reader-->>Step: FlatFileParseException
  Step->>Listener: onSkipInRead(error)
  Listener->>Port: recordRejected(PARSE_SKIP)
  Port->>DB: INSERT
else item read
  Reader-->>Step: Customer
  Step->>Processor: process(customer)
  alt result null
    Processor-->>Step: null
    Step->>Listener: afterProcess(input, null)
    Listener->>Port: recordRejected(POLICY_FILTER)
    Port->>DB: INSERT
  else result customer
    Processor-->>Step: transformed Customer
  end
end
```

---

# Application contracts

```mermaid
classDiagram
direction LR

class CustomerImportUseCase {
  <<interface>>
  +launchImport(inputFile)
  +getImportStatus(jobExecutionId)
  +getImportAuditReport(jobExecutionId, limit, offset)
}

class ImportAuditPort {
  <<interface>>
  +recordRejected(jobExecutionId, row)
  +countRejected(jobExecutionId)
  +loadRows(jobExecutionId, limit, offset)
}

class CustomerImportResult {
  +jobExecutionId
  +status
  +failures
  +readCount
  +writeCount
  +skipCount
  +filterCount
  +rejectedSample
}

class ImportAuditReport {
  +jobExecutionId
  +jobStatus
  +totalRejectedRows
  +rows
}

class RejectedRow {
  +category
  +lineNumber
  +reason
  +sourceId
  +sourceName
  +sourceEmail
}

CustomerImportUseCase --> CustomerImportResult
CustomerImportUseCase --> ImportAuditReport
ImportAuditPort --> RejectedRow
CustomerImportResult --> RejectedRow
ImportAuditReport --> RejectedRow
```

---

# Infrastructure adapters

```mermaid
flowchart LR
  STEP[CustomerImportJobConfig] --> LISTENER[CustomerImportAuditStepListener]
  LISTENER --> PORT[ImportAuditPort]
  JDBC[JdbcImportAuditPortAdapter] -.->|implements| PORT
  JDBC --> TX[TransactionTemplate REQUIRES_NEW]
  TX --> TABLE[(IMPORT_REJECTED_ROW)]
  UC[SpringBatchCustomerImportUseCase] --> PORT
  UC --> REPORT[ImportAuditReport]
  UC --> STATUS[CustomerImportResult]
```

The listener writes audit rows. The use case reads them for status and report APIs.

---

# Why `REQUIRES_NEW`?

Rejected-row audit is inserted using a separate transaction.

That makes the audit write independent from normal chunk transaction boundaries.

If audit insert fails, the code intentionally does **not** swallow it:

- operator visibility is part of the contract
- silent audit loss would be worse than a failed import
- schema/permission mistakes surface quickly

---

# Database schema

```mermaid
erDiagram
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

  BATCH_JOB_EXECUTION {
    NUMBER JOB_EXECUTION_ID PK
    VARCHAR2 STATUS
    TIMESTAMP START_TIME
    TIMESTAMP END_TIME
  }

  CUSTOMER {
    NUMBER ID PK
    VARCHAR2 NAME
    VARCHAR2 EMAIL
  }

  BATCH_JOB_EXECUTION ||--o{ IMPORT_REJECTED_ROW : "has rejected rows"
```

Oracle DDL lives in `schema.sql`. H2 smoke DDL lives in `schema-h2-import-audit-it.sql`.

---

# Status endpoint

`GET /api/batch/customer/import/{jobExecutionId}/status`

```json
{
  "jobExecutionId": 1,
  "status": "COMPLETED",
  "failures": [],
  "readCount": 4,
  "writeCount": 2,
  "skipCount": 1,
  "filterCount": 1,
  "rejectedSample": []
}
```

Use this for dashboards and quick progress checks.

---

# Report endpoint

`GET /api/batch/customer/import/{jobExecutionId}/report?limit=50&offset=0`

```json
{
  "jobExecutionId": 1,
  "jobStatus": "COMPLETED",
  "totalRejectedRows": 2,
  "rows": [
    {
      "category": "POLICY_FILTER",
      "lineNumber": 2,
      "reason": "Row filtered by import policy: email is missing or invalid (must contain '@').",
      "sourceId": "2",
      "sourceName": "Bob",
      "sourceEmail": "bademail"
    }
  ]
}
```

Use this for row-level investigation.

---

# HTTP response rules

```mermaid
flowchart TD
  A[GET status or report] --> B[JobExplorer.getJobExecution]
  B -->|null| C[404]
  B -->|found| D[Build response DTO]
  D --> E{Batch status FAILED?}
  E -->|yes| F[500 with DTO body]
  E -->|no| G[200 with DTO body]
```

The body stays parseable on `500`, so clients can still inspect partial audit evidence.

---

# Count semantics

| Field | Meaning |
|-------|---------|
| `readCount` | rows successfully converted to `Customer` |
| `writeCount` | processed items written to the writer |
| `skipCount` | Spring Batch skippable exceptions |
| `filterCount` | processor returned `null` |
| `rejectedSample` | first 10 persisted audit rows |
| `totalRejectedRows` | all audit rows for the job |

Read failures can increase `skipCount` without increasing `readCount`.

---

# Local H2 smoke

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=audit-it

curl -s -X POST \
  "http://localhost:8080/api/batch/customer/import?inputFile=classpath:customers-phase2-audit-sample.csv"

curl -s "http://localhost:8080/api/batch/customer/import/1/status" | jq .

curl -s "http://localhost:8080/api/batch/customer/import/1/report?limit=20&offset=0" | jq .
```

`audit-it` uses H2 and a no-op customer writer, so it exercises REST + batch + audit without Oracle.

---

# Verification

| Check | Purpose |
|-------|---------|
| `./mvnw clean verify` | full automated suite |
| `CustomerImportBatchAuditIntegrationTest` | batch + audit + H2 integration |
| `CustomerImportAuditStepListenerTest` | listener category mapping |
| Web MVC tests | HTTP mapping for status/report |
| manual `audit-it` smoke | end-to-end curl proof |

---

# What Phase 2 enables

Phase 2 creates the visibility layer needed before durable messaging:

- background jobs can be inspected after HTTP returns
- rejected rows are explainable
- operators can distinguish malformed input from domain policy rejection
- Phase 3 can safely queue work because reporting is already available
