# Slidev — Spring Batch demo (onion)

Engineer-facing deck aligned with `README.md`, `SD-ARCHITECTURE.md`, and the Java packages under `src/main/java/com/example/spring_batch_demo/`.

## Decks

| File | Focus |
|------|--------|
| **`slides.md`** | Full E2E: onion, Phase 1 async import, fault tolerance, polling |
| **`slides-phase2.md`** | Phase 2 deep dive: chunk hooks, **all HTTP flows** (request/response JSON), audit categories, DDL, listeners, `REQUIRES_NEW`, profiles, curl chain |

## Commands

```bash
cd slidev
npm install
npm run dev              # main deck
npm run dev:phase2       # Phase 2 deck
```

Build (CI / sanity):

```bash
cd slidev
npm install
npm run build            # main deck only
npm run build:phase2     # Phase 2 deck only
npm run build:all        # both decks → dist/ + dist-phase2/
```

Main bundle: **`dist/`** (`slides.md`). Phase 2 bundle: **`dist-phase2/`** (`slides-phase2.md`) so builds do not overwrite each other.

Dependencies use **`@slidev/cli`** (the `slidev` package name on npm was retired in favor of scoped packages). Theme: **`@slidev/theme-default`** (pin compatible with your CLI major if the build ever drifts).

> **Note:** `slidev/` is listed in the repo root `.gitignore` (local-only deck). Keep this directory updated when API, batch, or layer boundaries change.
