# Runbook: Spring Batch CSV → Oracle (Docker XE)

## 1) Prerequisites

- **Java 21** (set `JAVA_HOME` to Java 21 — avoid running with Java 25+ as Byte Buddy/Mockito compatibility issues may surface)
- Docker
- (Optional) IntelliJ Database tool window / SQL client

This repo uses the Maven Wrapper: `./mvnw`.

## 2) Oracle XE in Docker

### 2.1 Container status

You already have:

```text
oracle-db  (gvenzl/oracle-xe)  0.0.0.0:1521->1521
```

### 2.2 Create the application user (one-time)

`sqlplus` is inside the Oracle container; you do **not** need it on macOS.

```bash
docker exec -it oracle-db bash
sqlplus / as sysdba
```

In SQL*Plus, switch to the PDB and create the user:

```sql
SHOW CON_NAME;
ALTER SESSION SET CONTAINER = XEPDB1;
SHOW CON_NAME;

CREATE USER batch_user IDENTIFIED BY batch_pass;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE TRIGGER TO batch_user;
ALTER USER batch_user QUOTA UNLIMITED ON USERS;

SELECT username FROM dba_users WHERE username = 'BATCH_USER';
```

## 3) App configuration (where the DB settings live)

- `src/main/resources/application.properties`
  - default profile (production-safe): does **not** auto-create tables
- `src/main/resources/application-dev.properties`
  - dev profile: initializes schema and enables extra logs

Oracle connection used by the app:

- URL: `jdbc:oracle:thin:@//localhost:1521/XEPDB1`
- user: `batch_user`
- pass: `batch_pass`

## 4) Run the application

### 4.1 Dev mode (recommended while learning/testing)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

On startup in `dev` you should see:
- Oracle user diagnostics (connected user)
- SQL init debug logs

### 4.2 Non-dev mode (no schema auto-init)

```bash
./mvnw spring-boot:run
```

In this mode you must create tables yourself (or via migrations).

## 5) Use Postman (import API)

### 5.1 Endpoint

- Method: `POST`
- URL: `http://localhost:8080/api/batch/customer/import`
- Query param: `inputFile` (optional)

### 5.2 Import the default CSV bundled in the jar

In Postman:
- **POST** `http://localhost:8080/api/batch/customer/import`

This uses `classpath:customers.csv`.

### 5.3 Import your new CSV `customers-01.csv`

In Postman:
- **POST** `http://localhost:8080/api/batch/customer/import`
- Params:
  - key: `inputFile`
  - value: `classpath:customers-01.csv`

Or directly:

```bash
curl -X POST "http://localhost:8080/api/batch/customer/import?inputFile=classpath:customers-01.csv"
```

### 5.4 Import ANY local file on your Mac

Use the `file:` resource prefix with an **absolute path**:

```bash
curl -X POST "http://localhost:8080/api/batch/customer/import?inputFile=file:/Users/shubham.s/customers-any.csv"
```

Notes:
- Path must be accessible to the app process (your Mac filesystem).
- If your path has spaces, URL-encode it in Postman or curl.

## 6) How to verify (Oracle)

### 6.1 Connect as `batch_user` inside the container

```bash
docker exec -it oracle-db bash
sqlplus batch_user/batch_pass@//localhost:1521/XEPDB1
```

### 6.2 Check tables

```sql
SELECT table_name FROM user_tables WHERE table_name IN ('CUSTOMER', 'BATCH_JOB_EXECUTION');
```

### 6.3 Check imported rows

```sql
SELECT * FROM CUSTOMER ORDER BY ID;
```

Expected behavior:
- Rows with invalid email (no `@`) are filtered (not written).
- Names are uppercased.
- Reruns do not fail on duplicate IDs because writer uses `MERGE` (upsert).

### 6.4 Check Spring Batch metadata (did the job actually run?)

```sql
SELECT JOB_EXECUTION_ID, STATUS, START_TIME, END_TIME
FROM BATCH_JOB_EXECUTION
ORDER BY JOB_EXECUTION_ID DESC;
```

## 7) Internal flow (what calls what)

### 7.1 Request → Job launch

1. Postman hits `presentation.api.BatchJobController.importCustomers`
2. Controller delegates to `application.customer.CustomerImportUseCase`
3. Infrastructure implementation (`infrastructure.batch.SpringBatchCustomerImportUseCase`) builds `JobParameters` (`inputFile`, `run.at`)
4. Infrastructure calls `JobLauncher.run(customerJob, params)`

### 7.2 Job → Step → chunk loop

`customerJob` runs a single step `customerStep` (chunk size 10):

- Repeatedly:
  - Reader reads 1 CSV line → `Customer`
  - Processor validates/transforms → `Customer` or `null` (filtered)
  - Writer upserts the chunk into Oracle

### 7.3 Key code locations

- Presentation (API): `.../presentation/api/BatchJobController.java`
- Application use-case: `.../application/customer/CustomerImportUseCase.java`
- Job/Step wiring: `.../infrastructure/batch/CustomerImportJobConfig.java`
- Reader: `.../infrastructure/batch/CustomerCsvItemReaderConfig.java`
- Processor adapter: `.../infrastructure/batch/CustomerItemProcessorAdapter.java`
- Writer: `.../infrastructure/persistence/OracleCustomerWriterConfig.java`
- Listener logs: `.../infrastructure/batch/JobCompletionListener.java`

## 8) Tests

Tests are organized under `src/test/java/` with two top-level directories:

```
src/test/java/
├── unit/          ← fast, isolated (JUnit 5 + Mockito, no Spring context)
│   └── com/example/spring_batch_demo/
│       ├── SpringBatchDemoApplicationMainTest.java
│       ├── application/customer/
│       ├── domain/customer/
│       ├── infrastructure/batch/
│       ├── infrastructure/config/
│       ├── infrastructure/diagnostics/
│       ├── infrastructure/persistence/
│       └── presentation/api/BatchJobControllerTest.java
└── integration/   ← Spring-backed (@SpringBootTest, @WebMvcTest)
    └── com/example/spring_batch_demo/
        ├── SpringBatchDemoApplicationTests.java
        └── presentation/api/BatchJobControllerWebMvcIntegrationTest.java
```

### 8.1 Running tests

```bash
./mvnw clean test          # all tests
./mvnw clean install       # compile + test + package
./mvnw clean install -U    # same, force-update snapshots
```

### 8.2 Test database

Tests use **H2 in-memory** (Oracle-compat mode) — no running Oracle instance required.

Configuration: `src/test/resources/application-test.properties`

### 8.3 Mockito mock maker

The project forces the **subclass** mock maker via `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` (content: `mock-maker-subclass`). This avoids Byte Buddy `IllegalArgumentException` on JVMs newer than what the current Byte Buddy release supports. Trade-off: `mockStatic` is unavailable with the subclass mock maker.

### 8.4 Code coverage

JaCoCo is configured in `pom.xml`. After `mvn test`, reports are generated at `target/site/jacoco/index.html`.

## 9) Architecture notes (Onion / ports & adapters)

This codebase is being evolved toward **Onion Architecture** (dependencies pointing inward):

- **Domain**: pure business rules (e.g. customer validation policies)
- **Application**: orchestration/use-cases and ports (interfaces)
- **Infrastructure**: Spring Batch + JDBC + file/CSV adapters (framework details)
- **Presentation**: REST controller and API DTOs

See `SD-ARCHITECTURE.md` for the target package structure and refactor plan, while keeping the current root package `com.example.spring_batch_demo`.

## 10) Troubleshooting

### "200 OK but no rows inserted"
- Check the API response: it returns **500** on job failure (with reason).
- Check app logs for `JOB FINISHED ... status=...`.

### "No CUSTOMER table"
- Make sure you are connected to **service `XEPDB1`** (PDB) as `batch_user`.
- If you query as SYS/SYSTEM, `user_tables` shows tables for SYS/SYSTEM only.

### Duplicate key errors (ORA-00001)
- This project uses `MERGE` now, so reruns should not fail on duplicate IDs.

### `mvn clean install` fails with Byte Buddy / Mockito errors
- Ensure you are running with **Java 21** (`java -version`). Java 25+ triggers Byte Buddy incompatibilities.
- The subclass mock maker (`src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`) should already be in place.
- If using Java 25+, Mockito `5.23.0` with `mock-maker-subclass` is required (already configured in `pom.xml`).
