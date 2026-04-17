# Slidev — Spring Batch demo (onion)

Engineer-facing deck aligned with `README.md`, `SD-ARCHITECTURE.md`, and the Java packages under `src/main/java/com/example/spring_batch_demo/`.

## Commands

```bash
cd slidev
npm install
npm run dev
```

Build (CI / sanity):

```bash
cd slidev
npm install
npm run build
```

Dependencies use **`@slidev/cli`** (the `slidev` package name on npm was retired in favor of scoped packages). Theme: **`@slidev/theme-default`** (pin compatible with your CLI major if the build ever drifts).

> **Note:** `slidev/` is listed in the repo root `.gitignore` (local-only deck). Keep this directory updated when API, batch, or layer boundaries change.
