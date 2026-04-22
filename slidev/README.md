# Slidev — Spring Batch demo (onion)

Engineer-facing deck aligned with `README.md`, `SD-ARCHITECTURE.md`, and the Java packages under `src/main/java/com/example/spring_batch_demo/`.

## Canonical decks

| File | Focus |
|------|--------|
| **`deck-onion.md`** | Onion Architecture: layers, dependency rule, package responsibilities, ports/adapters, DB ownership |
| **`deck-phase1.md`** | Phase 1: direct REST-to-Batch import, CSV data, domain policy, customer writes, status, failures |
| **`deck-phase2.md`** | Phase 2: audit/reporting, listener hooks, rejected-row categories, report/status flows, DB schema |
| **`deck-phase3.md`** | Phase 3: RabbitMQ command boundary, topology, retry/DLQ, correlation lookup, queued flow |
| **`deck-overall-flows.md`** | Overall flows: all endpoints, branch selector, method chains, DB tables, response/exception matrix |

Legacy/reference decks still exist: `slides.md`, `slides-phase2.md`, `slides-phase2-flows.md`, `slides-phase3.md`, and `flows.md`.

## Commands

```bash
cd slidev
npm install
npm run dev:onion        # onion architecture
npm run dev:phase1       # Phase 1 core import
npm run dev:phase2       # Phase 2 audit/reporting
npm run dev:phase3       # Phase 3 RabbitMQ
npm run dev:overall      # overall flows
```

Build (CI / sanity):

```bash
cd slidev
npm install
npm run build:onion
npm run build:phase1
npm run build:phase2
npm run build:phase3
npm run build:overall
npm run build:all        # all decks
```

Bundles: **`dist-onion/`**, **`dist-phase1/`**, **`dist-phase2/`**, **`dist-phase3/`**, **`dist-overall-flows/`**.

Dependencies use **`@slidev/cli`** (the `slidev` package name on npm was retired in favor of scoped packages). Theme: **`@slidev/theme-default`** (pin compatible with your CLI major if the build ever drifts).

> **Note:** generated Slidev artifacts (`node_modules/`, `dist*`) are ignored, but the Markdown decks are visible to git so they do not get lost during cleanup.
