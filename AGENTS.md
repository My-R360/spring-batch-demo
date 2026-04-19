# AGENTS.md

## Cursor Cloud specific instructions

### Overview

Spring Batch demo application (Java 21 / Spring Boot 3.2.5) that reads customer CSV data, validates emails, uppercases names, and writes to an Oracle database. The batch job is triggered on-demand via `POST /api/batch/customer/import?inputFile=…` (non-blank resource location required).

### Architecture direction (important)

This project is being refactored toward **Onion Architecture** while keeping the root package:

- Root package: `com.example.spring_batch_demo`
- Target layering (**dependency rule**): dependencies point **inward**. **Domain** is the core; **application** (use-cases + ports) depends on domain; **infrastructure** (Batch, JDBC, adapters) and **presentation** (REST) depend on application/domain — not the reverse.

If you are making changes:
- Avoid introducing Spring/JDBC/Batch types into domain/application layers.
- Keep Batch wiring (Job/Step/Reader/Writer) in infrastructure.

### Prerequisites

- **Java 21** — pre-installed in the cloud environment.
- **Docker** — required to run Oracle XE. Must be installed with `fuse-overlayfs` storage driver and `iptables-legacy` for nested-container compatibility (see setup hints in system instructions).
- **Oracle XE** — run via Docker: `docker run -d --name oracle-xe -e ORACLE_PASSWORD=oracle123 -e APP_USER=batch_user -e APP_USER_PASSWORD=batch_pass -p 1521:1521 gvenzl/oracle-xe:21-slim`. Wait for `DATABASE IS READY TO USE` in logs before starting the app.

### Running the application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile auto-creates the `CUSTOMER` table and Spring Batch metadata tables on startup. The app runs on port **8080**.

### Key commands

| Action | Command |
|---|---|
| Resolve dependencies | `./mvnw dependency:resolve` |
| Compile | `./mvnw compile` |
| Run tests | `./mvnw test` |
| Run app (dev) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |
| Trigger batch job | `curl -X POST "http://localhost:8080/api/batch/customer/import?inputFile=classpath:customers.csv"` |

### Gotchas

- Tests use profile `test` with `src/main/resources/application-test.properties` (embedded H2, `MODE=Oracle`), so Oracle is not required for `./mvnw test` or `./mvnw spring-boot:run` when that profile is active.
- Running app startup tests can fail if port 8080 is already in use by another process.
- The Docker daemon must be started manually (`sudo dockerd &`) in cloud environments; it is not auto-started.
- Oracle XE takes ~30-60 seconds to initialize on first container start; poll `docker logs oracle-xe` for `DATABASE IS READY TO USE`.
