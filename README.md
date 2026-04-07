# Spring Batch CSV → Oracle (Docker XE)

This project imports customers from a CSV file into an Oracle database using **Spring Batch**. A REST API triggers the import so you can run it from **Postman**.

## What it does

- Reads CSV rows into `Customer` objects
- Filters invalid rows (email must contain `@`)
- Uppercases customer names
- Writes to Oracle table `CUSTOMER` using an **upsert** (`MERGE`) so reruns do not fail on duplicate IDs

## Quick start

### 1) Start Oracle in Docker

```bash
docker ps | grep oracle-db || true
```

If you don’t have it running yet, see `RUNBOOK.md`.

### 2) Run the app (dev profile recommended)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile:
- auto-creates `CUSTOMER` (idempotent)
- auto-creates Spring Batch metadata tables (`BATCH_*`)
- enables extra JDBC/batch logs

### 3) Trigger import (Postman/curl)

Default bundled CSV:

```bash
curl -X POST "http://localhost:8080/api/batch/customer/import"
```

Import a different classpath CSV (example: `src/main/resources/customers-01.csv`):

```bash
curl -X POST "http://localhost:8080/api/batch/customer/import?inputFile=classpath:customers-01.csv"
```

Import a file from your machine:

```bash
curl -X POST "http://localhost:8080/api/batch/customer/import?inputFile=file:/Users/shubham.s/customers.csv"
```

## Project structure (high level)

- **API trigger**: `src/main/java/com/example/spring_batch_demo/controller/BatchJobController.java`
- **Job/Step wiring**: `src/main/java/com/example/spring_batch_demo/config/BatchConfig.java`
- **Reader (CSV)**: `src/main/java/com/example/spring_batch_demo/reader/CustomerItemReader.java`
- **Processor (validate/transform)**: `src/main/java/com/example/spring_batch_demo/processor/CustomerProcessor.java`
- **Writer (Oracle MERGE)**: `src/main/java/com/example/spring_batch_demo/config/WriterConfig.java`
- **Job listener logs**: `src/main/java/com/example/spring_batch_demo/listener/JobCompletionListener.java`
- **Dev DB diagnostics**: `src/main/java/com/example/spring_batch_demo/diagnostics/DevStartupDiagnostics.java`
- **Schema init**: `src/main/resources/schema.sql`
- **Sample CSVs**: `src/main/resources/customers.csv`, `src/main/resources/customers-01.csv`

## Batch flow (conceptual)

HTTP POST → Controller → `JobLauncher.run(job, params)` → Job → Step (chunk loop) → Reader → Processor → Writer → Oracle.

See `RUNBOOK.md` for a detailed, step-by-step explanation and troubleshooting tips.

