# Microfrontend Architecture

## Stack
- ClojureScript (shadow-cljs) — compiles to ES modules
- Rspack — bundles and serves via Module Federation
- React + MUI — UI libraries
- re-frame — state management (owned by main, shared with remotes via `window.cljsMain`)

## Apps

| App  | Port | Role |
|------|------|------|
| main | 3001 | Host — owns all shared libs and re-frame app-db |
| app1 | 3002 | Remote — Counter widget |
| app2 | 3003 | Remote — Todo list |

## Rules

1. **main owns all dependencies** — React, react-dom, MUI, emotion, and re-frame live only in `main/package.json` as `dependencies` (npm) or in `main/shadow-cljs.edn` as Clojure dependencies. **app1 and app2 must not add any new Clojure or npm dependencies.**
2. **app1 and app2 have no runtime deps** — only `devDependencies` so shadow-cljs can compile. Rspack's Module Federation ensures main's copies are used at runtime.
3. **ClojureScript must use `:js-provider :import`** — keeps JS deps as real ES `import` statements so Rspack can deduplicate them via the shared singleton mechanism.
4. **Exports must use camelCase identifiers** — ClojureScript's `:exports` map keys become ES `export let` names; hyphens are invalid JS identifiers.
5. **Shared components and re-frame go through `window.cljsMain`** — main calls `expose-globals!` on init, attaching MUI component factories AND re-frame bridge functions to `window.cljsMain`. Remotes access them via JS interop, not a static ClojureScript `require`.
6. **main must start before app1/app2** — remotes depend on main's `remoteEntry.js` being available and on `window.cljsMain` being populated (including the re-frame db being initialised via `:initialise-db`).
7. **MF container name must not match any ClojureScript namespace prefix** — Rspack wraps each remoteEntry in an IIFE that hoists `var <name>;`. If `name` matches a ClojureScript namespace prefix (e.g. `"main"`), all ClojureScript code inside the IIFE resolves `main.components` against the hoisted `undefined` local variable instead of `window.main`. The host is therefore named `"mainApp"` in `main/rspack.config.js`, with remotes referencing it as `"mainApp@http://localhost:3001/remoteEntry.js"`.
8. **Both `remoteEntry.js` and `bundle.js` must be loaded in `index.html`** — Rspack emits the MF container bootstrap into `remoteEntry.js` and the actual application code (ClojureScript, React, MUI, re-frame) into `bundle.js`. `index.html` must include `<script src="/remoteEntry.js">` followed by `<script src="/bundle.js">` so that `expose-globals!` runs and `window.cljsMain` is populated before remotes are initialised.
9. **app1/app2 must stub shared cljs-runtime files as externals** — Each app's shadow-cljs output contains a full `js-out/cljs-runtime/` tree (goog bootstrap, `cljs.core`, SHADOW_ENV, etc.). Rspack's `externals` function resolves import paths to their absolute on-disk location and stubs any file under `/cljs-runtime/` that is NOT app-specific (i.e. does not start with `app1.`/`app2.`/`shadow.module.app1.` etc.) as `"globalThis" / "global"`. This prevents the double-execution of `goog.provide` that causes `Namespace "cljs.core" already declared`.
10. **Use reagent vector form `[component props]` for ClojureScript-wrapped MUI components** — Components returned by `shared/get-*` are ClojureScript functions (reagent wrappers). Use `[comp props children]`, not `[:> comp props children]`. The `[:>]` syntax is for raw JS React components only; passing a ClojureScript function to it emits Keyword objects as React children and crashes at render time.
11. **re-frame is a pure ClojureScript library — no npm package** — re-frame has no JavaScript distribution. It is declared only in `main/shadow-cljs.edn` as a Clojure dependency and is compiled into `js-out/main.js` by shadow-cljs. There is nothing to add to any `rspack.config.js` for it.
12. **Remotes use re-frame via the `rf.cljs` bridge, never by requiring re-frame directly** — Each remote (`app1`, `app2`) has its own `rf.cljs` which provides `dispatch`, `dispatch-with-payload`, `subscribe`, etc. These call the corresponding `rfDispatch`, `rfSubscribe`, … functions on `window.cljsMain`. This keeps re-frame's single app-db in main and avoids any attempt to load re-frame a second time.

## Build order (per app)

```
shadow-cljs compile  →  js-out/   →  rspack build  →  dist/
```

ClojureScript output is the Rspack entry point. Rspack does all bundling, optimization, and federation.

## Module Federation wiring

- **main** (`mainApp` container) exposes `./cljs-main` and individual MUI modules; all shared libs declared with `eager: true`.
- **app1 / app2** declare the same shared libs without `eager: true` and without local copies (`import: false` for app2). They reference main as `main: "mainApp@http://localhost:3001/remoteEntry.js"`.
- **index.html** (served by main's dev server) manually loads both remote entries via dynamic `<script>` tags, calls `app1Container.init(__webpack_share_scopes__.default)`, then `app1Container.get('./app1')` to mount each remote widget.

## re-frame bridge

`main/src/main/rf.cljs` owns the entire re-frame app-db and registers all events and subscriptions. On startup, `main.core/expose-globals!` attaches re-frame bridge functions to `window.cljsMain`:

| Property on `window.cljsMain` | Description |
|-------------------------------|-------------|
| `rfDispatch(jsVec)` | Dispatch an event from a JS string array, e.g. `["counter/increment"]` |
| `rfDispatchSync(jsVec)` | Synchronous version of `rfDispatch` |
| `rfDispatchWithPayload(kwStr, payload)` | Dispatch with a non-keyword payload, e.g. `(":todos/set-input", "hello")` |
| `rfSubscribe(kwStr)` | Subscribe and return a reagent reaction; deref with `@` in render |
| `rfRegEventDb(kwStr, fn)` | Register a new event-db handler from a remote |
| `rfRegSub(kwStr, fn)` | Register a new subscription from a remote |

Each remote wraps these in a thin `rf.cljs` namespace (`app1.rf` / `app2.rf`) that provides a ClojureScript-idiomatic API identical to re-frame's own.

## Key files

| File | Purpose |
|------|---------|
| `main/rspack.config.js` | Host Rspack config — MF container named `"mainApp"`, `eager: true` shared libs, exposes cljs-main + MUI modules |
| `app1/rspack.config.js` | Remote Rspack config — context-aware `externals` to stub shared cljs-runtime, references `mainApp` |
| `app2/rspack.config.js` | Remote Rspack config — same pattern as app1 |
| `main/public/index.html` | Loads `remoteEntry.js` + `bundle.js`, bootstraps remotes via MF container API |
| `main/src/main/core.cljs` | Calls `rf/init!` then `expose-globals!` which populates `window.cljsMain` with MUI factories + re-frame bridge |
| `main/src/main/rf.cljs` | re-frame app-db, all event handlers, all subscriptions, JS-callable bridge functions |
| `main/src/main/components.cljs` | Reagent wrappers for MuiButton, MuiTypography, MuiBox, MuiPaper |
| `app1/src/app1/shared.cljs` | Reads `window.cljsMain` to get MUI component factories from main |
| `app1/src/app1/rf.cljs` | re-frame bridge for app1 — delegates to `window.cljsMain` re-frame functions |
| `app2/src/app2/shared.cljs` | Same pattern as app1/shared.cljs |
| `app2/src/app2/rf.cljs` | re-frame bridge for app2 — delegates to `window.cljsMain` re-frame functions |

## Commands

```bash
make dev    # shadow-cljs watch + rspack serve
make build  # one-shot compile + bundle
make install
```
