---
name: spring-batch-onion-workflow
description: Implements and evolves the Spring Batch customer-import service using onion architecture boundaries, Oracle-safe batch patterns, and project documentation conventions. Use when adding features, refactoring layers, debugging import failures, or updating runbooks.
---

# Spring Batch Onion Workflow

## Purpose

Apply consistent implementation patterns for this project (works for **senior** refactors and **entry-level** onboarding when paired with `slidev/slides.md`):
- Onion architecture boundaries (`presentation`, `application`, `domain`, `infrastructure`)
- Spring Batch import flow (reader -> processor -> writer)
- Oracle persistence via application ports/adapters
- Required project docs updates

## Project anchors

- Root package: `com.example.spring_batch_demo`
- API endpoint: `POST /api/batch/customer/import`
- Input parameter: `inputFile` (`classpath:` or `file:` resource)
- Core docs: `README.md`, `RUNBOOK.md`, `SD-DESIGN.md`, `SD-ARCHITECTURE.md`, `PROMPTS.md`
- Optional deck: `slidev/` (see `.cursor/rules/slidev-deck.mdc` — keep slides E2E and diagrams accurate when code changes)

## Implementation checklist

Use this checklist for feature/refactor tasks:

1. Confirm which layer owns the change.
2. For **non-trivial** edits (multi-file, layer boundaries, ports/adapters, batch wiring), prefer **code-review-graph** (MCP/CLI) for impact before broad plans or wide reads (`.cursor/rules/code-review-graph.mdc`).
3. Keep controller thin; delegate to an application use-case.
4. Put business rules in domain policy/service (framework-free).
5. Keep Spring Batch/JDBC/Oracle logic in infrastructure adapters/config.
6. Use **explicit Java imports only**—no star imports (`.cursor/rules/java-imports.mdc`).
7. Preserve job/step identity unless migration is deliberate.
8. Keep writes idempotent for reruns (MERGE/upsert behavior).
9. Build and verify:
   - `./mvnw clean package -DskipTests`
10. Update docs + append `PROMPTS.md` entry.
11. If `slidev/` is present: refresh `slidev/slides.md` (narrative + Mermaid) for the same behavior change; run `cd slidev && npx slidev build slides.md`.

## Debug workflow

When import fails:

1. Check API response payload (`status`, `failures`).
2. Check job logs from `JobCompletionListener`.
3. Verify DB connectivity/profile config (`application-dev.properties`).
4. Verify `inputFile` resolution and CSV format (`id,name,email`).
5. Validate expected filtering behavior in domain policy.

## Output expectations for coding tasks

- Explain *where* the change belongs (layer + reason).
- Mention affected files and why.
- Include verification command(s).
- If behavior changed, update runbook/docs.

