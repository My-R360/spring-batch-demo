# SD: Design (Patterns + SOLID)

This document explains key design choices and where they appear in the current onion-architecture implementation.

## SOLID principles

### Single Responsibility Principle (SRP)

- `BatchJobController` handles HTTP request/response and delegates work.
  - `src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java`
- `CustomerCsvItemReaderConfig` builds and configures the CSV reader bean.
  - `src/main/java/com/example/spring_batch_demo/infrastructure/batch/CustomerCsvItemReaderConfig.java`
- `CustomerItemProcessorAdapter` only bridges Batch processor SPI to domain policy.
  - `src/main/java/com/example/spring_batch_demo/infrastructure/batch/CustomerItemProcessorAdapter.java`
- `EmailAndNameCustomerImportPolicy` contains business rule logic (email validity + uppercase name).
  - `src/main/java/com/example/spring_batch_demo/domain/customer/EmailAndNameCustomerImportPolicy.java`
- `OracleCustomerUpsertPortAdapter` contains Oracle/JDBC persistence logic only.
  - `src/main/java/com/example/spring_batch_demo/infrastructure/persistence/OracleCustomerUpsertPortAdapter.java`

### Open/Closed Principle (OCP)

- Business rule is behind `CustomerImportPolicy`; new policies can be added without changing batch adapter flow.
  - `src/main/java/com/example/spring_batch_demo/domain/customer/CustomerImportPolicy.java`

### Liskov Substitution Principle (LSP)

- Any `CustomerImportPolicy` implementation can replace another if it honors contract: return transformed `Customer` or `null` to filter.
- Any `CustomerUpsertPort` implementation can replace Oracle adapter while keeping application layer unchanged.

### Interface Segregation Principle (ISP)

- `CustomerImportPolicy` has a single focused method.
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

### Builder

- `JobBuilder` and `StepBuilder` for job/step wiring.
- `FlatFileItemReaderBuilder` for CSV reader creation.

### Observer/Listener

- `JobCompletionListener` logs before/after job lifecycle events.
  - `src/main/java/com/example/spring_batch_demo/infrastructure/batch/JobCompletionListener.java`

## Persistence notes

- Oracle upsert uses `MERGE`, so reruns update existing IDs instead of failing with ORA-00001.
  - `src/main/java/com/example/spring_batch_demo/infrastructure/persistence/OracleCustomerUpsertPortAdapter.java`

## Diagnostics

- `DevStartupDiagnostics` reports DB user and table visibility in `dev`.
  - `src/main/java/com/example/spring_batch_demo/infrastructure/diagnostics/DevStartupDiagnostics.java`

