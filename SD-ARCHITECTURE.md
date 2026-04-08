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

## Current code mapping (as-is)

Today, the project is functional and has been refactored into onion-style layers. Key files:

- Presentation:
  - `presentation/api/BatchJobController.java` (HTTP → calls application use-case)
- Application:
  - `application/customer/CustomerImportUseCase.java`
  - `application/customer/CustomerImportResult.java`
- Domain:
  - `domain/customer/Customer.java`
  - `domain/customer/CustomerImportPolicy.java`
  - `domain/customer/EmailAndNameCustomerImportPolicy.java`
- Infrastructure (batch + persistence + diagnostics):
  - `infrastructure/batch/CustomerImportJobConfig.java`
  - `infrastructure/batch/CustomerCsvItemReaderConfig.java`
  - `infrastructure/batch/CustomerItemProcessorAdapter.java`
  - `infrastructure/persistence/OracleCustomerWriterConfig.java`
  - `infrastructure/batch/JobCompletionListener.java`
  - `infrastructure/diagnostics/DevStartupDiagnostics.java`

## Target package structure (approved direction)

This is the proposed target structure for refactor (keeping root package):

```
com.example.spring_batch_demo
├── SpringBatchDemoApplication.java
│
├── domain
│   └── customer
│       ├── Customer.java
│       └── CustomerImportPolicy.java (or domain validation service)
│
├── application
│   └── customer
│       ├── port
│       │   ├── CustomerUpsertPort.java
│       │   └── CustomerSourcePort.java (optional)
│       └── CustomerImportUseCase.java (or ApplicationService)
│
├── infrastructure
│   ├── batch
│   │   ├── CustomerImportJobConfig.java
│   │   ├── CustomerCsvItemReaderConfig.java
│   │   ├── CustomerItemProcessorAdapter.java
│   │   └── JobCompletionListener.java
│   ├── persistence
│   │   └── OracleCustomerUpsertAdapter.java
│   └── diagnostics
│       └── DevStartupDiagnostics.java
│
└── presentation
    └── api
        └── BatchJobController.java
```

Notes:
- Spring Batch types (`Job`, `Step`, `ItemReader`, `ItemWriter`) live under `infrastructure.batch`.
- Oracle/JDBC specifics live under `infrastructure.persistence`.
- Domain stays plain Java and can be unit-tested without Spring.

## Data flow (high level)

1. HTTP request → `presentation.api.BatchJobController`
2. Controller calls the application use-case (or launches job via a small application service)
3. Batch job executes:
   - Reader reads CSV → produces `domain.customer.Customer`
   - Processor delegates to domain policy → returns Customer or filters
   - Writer uses application port → infrastructure adapter upserts to Oracle

## Refactor plan (high level)

When implementation begins, we will refactor in small steps:

- Introduce `domain` and move pure model/rules there (no Spring annotations).
- Introduce `application` ports for persistence (and optionally for customer source).
- Move Batch wiring to `infrastructure.batch` and make processor/writer act as adapters.
- Move controller to `presentation.api` and keep it thin.
- Update docs and verify with:
  - `./mvnw clean package`
  - Postman import calls
  - Oracle queries for `CUSTOMER` and `BATCH_*`

## Further reading (Onion Architecture)

- Expedia Group Tech: “Onion Architecture — Let’s slice it like a Pro”  
  `https://medium.com/expedia-group-tech/onion-architecture-deed8a554423`
  - Emphasizes **dependency direction** (outer → inner), **data encapsulation**, and choosing a **pragmatic** set of layers.
  - Useful takeaway for this repo: keep Spring Batch + JDBC at the edges; keep inner logic independent and testable.

- DEV Community: “Onion Architecture in Domain-Driven Design (DDD)”  
  `https://dev.to/yasmine_ddec94f4d4/onion-architecture-in-domain-driven-design-ddd-35gn`
  - Frames Onion as a way to **protect the domain model** via **dependency inversion** and clear separation of concerns.
  - Useful takeaway for this repo: define ports/interfaces inward, implement adapters outward.

