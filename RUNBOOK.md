# Runbook: Spring Batch CSV → Oracle (Docker XE)

## 1) Prerequisites

- Java 21+
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

1. Postman hits `BatchJobController.importCustomers`
2. Controller builds `JobParameters` (`inputFile`, `run.at`)
3. Controller calls `JobLauncher.run(customerJob, params)`

### 7.2 Job → Step → chunk loop

`customerJob` runs a single step `customerStep` (chunk size 10):

- Repeatedly:
  - Reader reads 1 CSV line → `Customer`
  - Processor validates/transforms → `Customer` or `null` (filtered)
  - Writer upserts the chunk into Oracle

### 7.3 Key code locations

- API trigger: `.../controller/BatchJobController.java`
- Job/Step wiring: `.../config/BatchConfig.java`
- Reader: `.../reader/CustomerItemReader.java`
- Processor: `.../processor/CustomerProcessor.java`
- Writer: `.../config/WriterConfig.java`
- Listener logs: `.../listener/JobCompletionListener.java`

## 8) Troubleshooting

### “200 OK but no rows inserted”
- Check the API response: it returns **500** on job failure (with reason).
- Check app logs for `JOB FINISHED ... status=...`.

### “No CUSTOMER table”
- Make sure you are connected to **service `XEPDB1`** (PDB) as `batch_user`.
- If you query as SYS/SYSTEM, `user_tables` shows tables for SYS/SYSTEM only.

### Duplicate key errors (ORA-00001)
- This project uses `MERGE` now, so reruns should not fail on duplicate IDs.

