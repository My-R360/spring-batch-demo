# SD: Design (Patterns + SOLID)

This document explains the main design patterns and SOLID principles used in this project, and where they show up in the code.

## SOLID principles

### Single Responsibility Principle (SRP)

- **`BatchJobController`**: responsible only for accepting an HTTP request, building `JobParameters`, and launching the job.
  - File: `src/main/java/com/example/spring_batch_demo/controller/BatchJobController.java`
- **`CustomerItemReader`**: responsible for converting an `inputFile` string parameter into a Spring `Resource` and building a CSV reader.
  - File: `src/main/java/com/example/spring_batch_demo/reader/CustomerItemReader.java`
- **`CustomerProcessor` + `EmailValidationStrategy`**: responsible for validation and transformation (filter invalid emails; uppercase name).
  - Files:
    - `src/main/java/com/example/spring_batch_demo/processor/CustomerProcessor.java`
    - `src/main/java/com/example/spring_batch_demo/processor/EmailValidationStrategy.java`
- **`WriterConfig`**: responsible for persistence mechanics (Oracle SQL `MERGE` upsert via JDBC batch writer).
  - File: `src/main/java/com/example/spring_batch_demo/config/WriterConfig.java`

### Open/Closed Principle (OCP)

- Validation is modeled as a **strategy** (`ValidationStrategy`) so you can add new validation logic without changing `CustomerProcessor` logic beyond selecting a different strategy.
  - File: `src/main/java/com/example/spring_batch_demo/processor/ValidationStrategy.java`

### Liskov Substitution Principle (LSP)

- Any `ValidationStrategy` implementation must obey the contract: `isValid(customer)` returns `true` for valid records and `false` for invalid ones.
  - This ensures `CustomerProcessor` can use any strategy interchangeably.

### Interface Segregation Principle (ISP)

- `ValidationStrategy` is intentionally small and focused: one method, one responsibility.

### Dependency Inversion Principle (DIP)

- The project is being evolved toward onion layering where higher-level “policy” code should not depend on framework details.
- Today, strategy interfaces already help decouple the processor from the specific validation implementation.

## Design patterns

### Strategy

Used for validation behavior.

- **Strategy interface**: `ValidationStrategy`
- **Concrete strategy**: `EmailValidationStrategy`
- **Context**: `CustomerProcessor` delegates validation via `ValidationStrategy`

Files:
- `src/main/java/com/example/spring_batch_demo/processor/ValidationStrategy.java`
- `src/main/java/com/example/spring_batch_demo/processor/EmailValidationStrategy.java`
- `src/main/java/com/example/spring_batch_demo/processor/CustomerProcessor.java`

### Adapter (framework integration)

Spring Batch itself is a ports/adapters-style framework: you implement its SPI interfaces and it calls you.

Examples:
- `CustomerProcessor` adapts your validation/transformation logic to the `ItemProcessor` SPI.
- `JdbcBatchItemWriter` adapts persistence SQL to the `ItemWriter` SPI (configured in `WriterConfig`).

### Template Method (framework-provided)

Chunk-oriented step execution is a classic template method:

- Spring Batch controls the algorithm: read → process → write in chunks.
- You provide the steps of the algorithm via `ItemReader`, `ItemProcessor`, and `ItemWriter`.

### Builder (framework-provided)

Builders are used to create configuration objects fluently:
- `JobBuilder` / `StepBuilder` (Job/Step wiring)
- `FlatFileItemReaderBuilder` (CSV reader)
- `JdbcBatchItemWriterBuilder` (JDBC writer)

### Observer/Listener

`JobExecutionListener` is a listener hook for cross-cutting concerns (logging).
- File: `src/main/java/com/example/spring_batch_demo/listener/JobCompletionListener.java`

## Persistence behavior notes

### Upsert to avoid duplicate key failures

The writer uses Oracle `MERGE` so repeated imports of the same IDs update existing rows instead of failing with ORA-00001.

- File: `src/main/java/com/example/spring_batch_demo/config/WriterConfig.java`

## Logging/diagnostics

- `DevStartupDiagnostics` logs the connected Oracle user and table visibility when the `dev` profile is active.
  - File: `src/main/java/com/example/spring_batch_demo/diagnostics/DevStartupDiagnostics.java`

