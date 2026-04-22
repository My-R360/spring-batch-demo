---
theme: default
title: Spring Batch Demo - Phase 3 RabbitMQ
info: |
  RabbitMQ command boundary: enqueue, consume, launch batch, register correlation, DLQ on exhausted listener retry.
---

# Phase 3 - RabbitMQ boundary

Phase 3 moves the HTTP-to-batch handoff behind a durable message boundary.

The API no longer needs to start the batch job before returning in `dev`.

---

# Why RabbitMQ here?

- absorbs bursts of import requests
- makes accepted work durable outside the HTTP request
- gives operators a DLQ for poison messages
- decouples caller latency from batch launch latency
- keeps batch chunk retry separate from message-level retry

---

# Before vs after

| Step | Phase 1/2 direct launch | Phase 3 `dev` |
|------|--------------------------|---------------|
| POST | calls launcher path in-process | publishes command to RabbitMQ |
| 202 body | usually has `jobExecutionId` | has `correlationId`, status `QUEUED` |
| job id | known immediately | known after listener launches job |
| retry boundary | batch chunk retry | message retry + batch chunk retry |
| failed message | HTTP failure | message can reach DLQ |

---

# Component map

```mermaid
flowchart LR
  Client[Client] --> API[BatchJobController]
  API --> Port[CustomerImportCommandPublisher]
  Port --> Pub[AmqpCustomerImportCommandPublisher]
  Pub --> Ex[DirectExchange customer.import.commands]
  Ex --> Q[Queue customer.import.queue]
  Q --> L[CustomerImportJobLaunchListener]
  L --> UC[CustomerImportUseCase]
  UC --> Batch[async JobLauncher + customerJob]
  L --> Corr[ImportLaunchCorrelationPort]
  Corr --> DB[(IMPORT_LAUNCH_CORRELATION)]
  Q -->|exhausted retry| DLX[customer.import.dlx]
  DLX --> DLQ[customer.import.dlq]
```

---

# Topology

| Piece | Default value |
|-------|---------------|
| Exchange | `customer.import.commands` |
| Routing key | `customer.import.command` |
| Work queue | `customer.import.queue` |
| DLX | `customer.import.dlx` |
| DLQ | `customer.import.dlq` |
| DLQ routing key | `customer.import.dlq` |

Declared by `CustomerImportRabbitConfig` when `app.messaging.customer-import.enabled=true`.

---

# POST sequence in `dev`

```mermaid
sequenceDiagram
autonumber
actor Client
participant API as BatchJobController
participant Pub as AmqpCustomerImportCommandPublisher
participant Stage as InputFileStagingPort
participant MQ as RabbitMQ
participant Listener as CustomerImportJobLaunchListener
participant UC as CustomerImportUseCase
participant Corr as ImportLaunchCorrelationPort

Client->>API: POST /customer/import?inputFile=...
API->>API: validate input + create correlationId
API->>Pub: publish(CustomerImportCommand)
Pub->>Stage: stageForImport(inputFile, correlationId)
Stage-->>Pub: staged classpath location or original classpath
Pub->>MQ: convertAndSend(exchange, routingKey, staged command)
Pub-->>API: QUEUED + correlationId
API-->>Client: 202 Accepted
MQ-->>Listener: deliver command
Listener->>UC: launchImport(staged inputFile)
UC-->>Listener: jobExecutionId
Listener->>Corr: registerLaunchedJob(correlationId, jobExecutionId)
```

---

# Command payload

```json
{
  "correlationId": "6f60f76d-cfd9-4e6f-9dcf-2e6d2b9d9bb1",
  "inputFile": "classpath:customers-phase2-audit-sample.csv",
  "schemaVersion": 1
}
```

`schemaVersion` gives room for message evolution without breaking old consumers.

---

# Correlation lookup

```mermaid
flowchart TD
  A[POST returns correlationId] --> B[Client polls by-correlation endpoint]
  B --> C{IMPORT_LAUNCH_CORRELATION row exists?}
  C -->|no| D[404: listener has not launched job yet]
  C -->|yes| E[200: jobExecutionId]
  E --> F[Client polls status/report by jobExecutionId]
```

---

# Correlation table

```mermaid
erDiagram
  IMPORT_LAUNCH_CORRELATION {
    VARCHAR2 CORRELATION_ID PK
    NUMBER JOB_EXECUTION_ID
    TIMESTAMP CREATED_AT
  }

  BATCH_JOB_EXECUTION {
    NUMBER JOB_EXECUTION_ID PK
    VARCHAR2 STATUS
  }

  BATCH_JOB_EXECUTION ||--o| IMPORT_LAUNCH_CORRELATION : maps
```

Insert is "if absent" so redelivery does not create duplicate mappings.

---

# Retry and DLQ

```mermaid
stateDiagram-v2
  [*] --> Delivered
  Delivered --> ListenerRunning
  ListenerRunning --> Acked: listener returns successfully
  ListenerRunning --> Retry: exception
  Retry --> ListenerRunning: attempts remaining + backoff
  Retry --> Rejected: attempts exhausted
  Rejected --> DLQ: RejectAndDontRequeueRecoverer
  Acked --> [*]
  DLQ --> [*]
```

Message retry is separate from Spring Batch chunk retry.

---

# Listener tuning

| Setting | Default |
|---------|---------|
| Prefetch | `1` |
| Listener concurrency | `1` |
| Retry max attempts | `4` |
| Initial interval | `1000ms` |
| Multiplier | `2.0` |
| Max interval | `10000ms` |
| Recoverer | `RejectAndDontRequeueRecoverer` |

---

# Profile behavior

| Profile | Publisher bean | POST status | Needs RabbitMQ? |
|---------|----------------|-------------|-----------------|
| `dev` | `AmqpCustomerImportCommandPublisher` | `QUEUED` | yes |
| default | `DirectCustomerImportCommandPublisher` | `STARTED` | no |
| `audit-it` | direct publisher | `STARTED` | no |
| `test` | direct or mocked path | test-specific | no |
| `amqp-it` | AMQP path | `QUEUED` | Testcontainers |

---

# End-to-end `dev` curl path

```bash
# 1. Start RabbitMQ and Oracle first
docker compose -f docker-compose.rabbitmq.yml up -d

# 2. Run app
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. POST import
curl -s -X POST \
  "http://localhost:8080/api/batch/customer/import?inputFile=classpath:customers-phase2-audit-sample.csv"

# 4. Resolve job id
curl -s "http://localhost:8080/api/batch/customer/import/by-correlation/<uuid>/job"
```

---

# Operational checks

| Check | Where |
|-------|-------|
| command published | RabbitMQ exchange/queue metrics |
| stuck command | `customer.import.queue` depth |
| poison message | `customer.import.dlq` depth |
| job launched | `IMPORT_LAUNCH_CORRELATION` |
| job progress | `BATCH_JOB_EXECUTION`, status endpoint |
| row rejections | `IMPORT_REJECTED_ROW`, report endpoint |

---

# Failure branches

```mermaid
flowchart TD
  A[POST request] --> B{input valid?}
  B -->|no| C[400]
  B -->|yes| D{publish ok?}
  D -->|no| E[500 publish failure]
  D -->|yes| F[202 QUEUED]
  F --> G[Listener consumes]
  G --> H{launch ok?}
  H -->|yes| I[register correlation]
  H -->|no, retry left| J[retry message]
  J --> G
  H -->|no, exhausted| K[DLQ]
```

---

# What stays unchanged

RabbitMQ only changes the launch boundary.

These stay the same:

- `CustomerImportUseCase.launchImport`
- `customerJob` / `customerStep`
- reader / processor / writer
- audit listener and report API
- status endpoint once `jobExecutionId` is known

---

# Next hardening ideas

- expose DLQ replay tooling
- add correlation id to all structured logs
- add Micrometer metrics for queue depth, launch latency, DLQ count
- add idempotency policy for repeated `inputFile`
- move DDL to Flyway/Liquibase
