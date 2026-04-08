# PROMPTS / CHANGE LOG

This file tracks what was requested in chat and what was changed in the project, in chronological order.

## How to maintain this file

- Add a new entry for each meaningful request/change.
- Keep entries chronological.
- For each entry, capture:
  - prompt/request summary
  - files/components changed
  - outcome/notes

---

## Timeline

### 01) Initial Spring Batch + Oracle review
- **Prompt summary**: Review existing Spring Batch CSV-to-DB code, suggest/fix corrections, and provide Oracle Docker setup/run steps.
- **Changes done**:
  - Corrected invalid Spring Batch imports/packages.
  - Fixed class/file placement and naming issues.
  - Added Oracle datasource configuration and JDBC driver setup.
  - Added schema initialization and detailed run steps.
- **Outcome**: Project became buildable/runnable with Oracle and manual run instructions.

### 02) Add REST trigger + configurable CSV input
- **Prompt summary**: Add endpoint trigger and support different input files.
- **Changes done**:
  - Added REST API endpoint to trigger import.
  - Added `inputFile` request parameter support.
  - Reader supports `classpath:` and `file:` resource locations.
  - Switched startup behavior so job runs on API trigger.
- **Outcome**: Import can be executed via Postman/curl with selectable file.

### 03) Build fixes (`mvn clean install` errors)
- **Prompt summary**: Fix Maven build issues.
- **Changes done**:
  - Replaced invalid dependencies with valid Spring Boot/Spring Batch artifacts.
  - Fixed compile issues caused by Lombok-generated methods/constructors not available.
  - Stabilized project build.
- **Outcome**: `./mvnw clean install` succeeded.

### 04) Dev profile safety for restarts
- **Prompt summary**: Make dev configuration restart-safe.
- **Changes done**:
  - Added/updated `application-dev.properties`.
  - Adjusted schema init behavior for development.
  - Added safer defaults in main profile.
- **Outcome**: Better local restart behavior.

### 05) Diagnostics/logging for troubleshooting
- **Prompt summary**: Explain full flow and add logs to detect failures.
- **Changes done**:
  - Added startup diagnostics for DB/user/table visibility.
  - Improved controller/job logging and failure reporting.
  - Improved response behavior for failed job executions.
- **Outcome**: Clear visibility into where failures occur.

### 06) Duplicate key failure fix
- **Prompt summary**: Fix ORA-00001 duplicate key on reruns.
- **Changes done**:
  - Replaced insert-only behavior with Oracle `MERGE` upsert behavior.
- **Outcome**: Re-running imports no longer fails for existing IDs.

### 07) GitHub repository setup
- **Prompt summary**: Create private repo under org and push code.
- **Changes done**:
  - Initialized git, configured remote, committed and pushed to private org repo.
  - Added `.gitignore` updates for local/non-project artifacts.
- **Outcome**: Project published privately in GitHub org.

### 08) Documentation expansion
- **Prompt summary**: Add setup/run docs and operational guidance.
- **Changes done**:
  - Added `README.md` and `RUNBOOK.md`.
  - Added architecture/design docs:
    - `SD-DESIGN.md`
    - `SD-ARCHITECTURE.md`
- **Outcome**: Project now has user/dev/operator documentation.

### 09) Onion architecture planning and refactor
- **Prompt summary**: Plan first, then refactor to onion architecture with SOLID/patterns.
- **Changes done**:
  - Introduced layers while keeping root package:
    - `domain`
    - `application`
    - `infrastructure`
    - `presentation`
  - Moved/rewired controller, batch use-case, reader/processor/writer adapters, configs, diagnostics.
  - Removed obsolete/unused classes and empty folders.
- **Outcome**: Layered architecture now implemented and build verified.

### 10) Add comments + Lombok support
- **Prompt summary**: Add method/interface comments and Lombok where appropriate.
- **Changes done**:
  - Enabled Lombok annotation processing in Maven build config.
  - Applied Lombok selectively (`@Data`, `@RequiredArgsConstructor`, `@Slf4j`) where useful.
  - Added Javadocs/comments for key interfaces/methods and configuration points.
- **Outcome**: Cleaner code and better readability/documentation.

### 11) Introduce application persistence port
- **Prompt summary**: Improve SOLID by adding an application port for persistence.
- **Changes done**:
  - Added `CustomerUpsertPort` in application layer.
  - Implemented Oracle adapter in infrastructure.
  - Batch writer now delegates through the application port.
- **Outcome**: Better dependency inversion and testability.

### 12) Add external architecture references
- **Prompt summary**: Read onion architecture resources and add references.
- **Changes done**:
  - Read supplied links.
  - Added “Further reading” section in architecture doc with takeaways.
- **Outcome**: Architecture docs now include reference context.

### 13) Current request: add this prompts file
- **Prompt summary**: Create a prompts file that tracks all requests/changes chronologically and keep documenting future changes.
- **Changes done**:
  - Added this `PROMPTS.md` file.
  - Backfilled major historical requests and project changes.
  - Added maintenance guidance at top.
- **Outcome**: Prompt-to-change traceability is now documented in-repo.

### 14) Add Cursor project rules and skill
- **Prompt summary**: Create reusable Cursor rules and skills tailored to this project for future development.
- **Changes done**:
  - Added project rules under `.cursor/rules/`:
    - `onion-architecture.mdc`
    - `spring-batch-conventions.mdc`
    - `docs-and-prompt-log.mdc`
  - Added project skill under `.cursor/skills/`:
    - `spring-batch-onion-workflow/SKILL.md`
- **Outcome**: Future Cursor sessions can auto-apply project-specific architecture and workflow guidance.

---

## Later backlog (requested but deferred)

1. Make import API asynchronous for scalability (launch + status endpoint pattern).
2. Add reporting/audit pipeline for event interactions and rejected rows/reasons.
3. Add design-first OpenAPI workflow with generated DTOs + interfaces (as modules in same repo, not as a separate service).

