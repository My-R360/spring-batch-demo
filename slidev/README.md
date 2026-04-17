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

> **Note:** On branch **`onion-spring-batch-01`**, this folder is **tracked** in git (root `.gitignore` does not exclude `slidev/`). On other branches the repo may still ignore `slidev/`—merge `.gitignore` intentionally. Keep the deck updated when API, batch, or layer boundaries change.
