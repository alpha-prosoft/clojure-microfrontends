# ClojureScript Microfrontends with Rspack Module Federation

> A step-by-step guide to building independently-deployable microfrontends in ClojureScript, sharing React, MUI, and a single re-frame app-db across process boundaries — without duplication.

---

## Table of Contents

1. [What we are building](#1-what-we-are-building)
2. [Why this stack is hard](#2-why-this-stack-is-hard)
3. [Repository layout](#3-repository-layout)
4. [The build pipeline](#4-the-build-pipeline)
5. [shadow-cljs configuration](#5-shadow-cljs-configuration)
6. [Rspack and Module Federation configuration](#6-rspack-and-module-federation-configuration)
7. [The cljs-runtime problem — and how externals solves it](#7-the-cljs-runtime-problem--and-how-externals-solves-it)
8. [The container name collision — why main is named mainApp](#8-the-container-name-collision--why-main-is-named-mainapp)
9. [Sharing React, MUI and re-frame at runtime](#9-sharing-react-mui-and-re-frame-at-runtime)
10. [The window.cljsMain bridge](#10-the-windowcljsmain-bridge)
11. [The re-frame bridge](#11-the-re-frame-bridge)
12. [index.html — bootstrapping order matters](#12-indexhtml--bootstrapping-order-matters)
13. [Production build and bundle analysis](#13-production-build-and-bundle-analysis)
14. [Key rules summarised](#14-key-rules-summarised)

---

## 1. What we are building

Three independently-served web applications:

| App    | Port | Role                           |
|--------|------|--------------------------------|
| `main` | 3001 | **Host** — owns all shared libraries and the re-frame app-db |
| `app1` | 3002 | **Remote** — Counter widget    |
| `app2` | 3003 | **Remote** — Todo list         |

`main` is the shell. It serves the HTML page, provides React 18, MUI 5, emotion, reagent and re-frame. `app1` and `app2` are lazy-loaded at runtime and share every library from `main` — they ship **zero** React or MUI bytes of their own.

---

## 2. Why this stack is hard

Combining ClojureScript with Rspack Module Federation introduces three problems that do not exist in a pure JavaScript microfrontend:

**Problem 1 — Two-phase build.**  
ClojureScript (via shadow-cljs) must compile to plain JavaScript first. That JavaScript is then the entry point for Rspack. You cannot skip either step.

**Problem 2 — ClojureScript runtime globals execute twice.**  
The shadow-cljs `:esm` target emits a `cljs-runtime/` directory full of side-effect files: the Google Closure bootstrap, `cljs.core`, `SHADOW_ENV`, etc. Every ES module in every app imports them. If both `main` and `app1` bundle these files independently the browser executes `goog.provide("cljs.core")` twice and crashes with `Namespace "cljs.core" already declared`.

**Problem 3 — Container name vs. ClojureScript namespace prefix.**  
Rspack wraps every Module Federation `remoteEntry.js` in an IIFE that starts with `var <containerName>;`. If `containerName` is `"main"`, every ClojureScript line that references `main.components` resolves against the local IIFE variable (`undefined`) instead of `window.main`. The app silently breaks.

All three problems have clean solutions, described in detail below.

---

## 3. Repository layout

```
microfrontend/
├── main/            # Host application
│   ├── shadow-cljs.edn
│   ├── rspack.config.js
│   ├── package.json
│   ├── public/index.html
│   └── src/main/
│       ├── core.cljs     # entry point, calls rf/init! and expose-globals!
│       ├── rf.cljs       # re-frame app-db + JS-callable bridge functions
│       └── components.cljs  # reagent wrappers for MUI components
├── app1/            # Remote — Counter widget
│   ├── shadow-cljs.edn
│   ├── rspack.config.js
│   ├── package.json
│   └── src/app1/
│       ├── core.cljs   # mount function
│       ├── views.cljs  # counter UI
│       ├── shared.cljs # reads window.cljsMain for MUI components
│       └── rf.cljs     # re-frame bridge via window.cljsMain
└── app2/            # Remote — Todo list (same structure as app1)
```

---

## 4. The build pipeline

Each application goes through exactly two phases:

```
shadow-cljs compile  →  js-out/   →  rspack build  →  dist/
```

**Phase 1 — shadow-cljs** compiles ClojureScript source to ES modules in `js-out/`. It emits:
- `js-out/main.js` (or `app1.js` / `app2.js`) — the application entry module
- `js-out/cljs-runtime/` — ~228 files for `main`, ~132 for remotes — the ClojureScript/Google Closure runtime

**Phase 2 — Rspack** takes `js-out/main.js` as its entry point, follows all `import` statements, applies Module Federation sharing, and emits the final `dist/` bundles.

The scripts in each `package.json` reflect this order:

```json
"build": "npm run cljs:build && npm run rspack:build"
```

For development, shadow-cljs runs in watch mode and Rspack serves with `rspack:dev`:

```json
"dev": "npm run cljs:watch & npm run rspack:dev"
```

---

## 5. shadow-cljs configuration

### `main/shadow-cljs.edn`

```clojure
{:source-paths ["src"]
 :dependencies [[org.clojure/clojurescript "1.12.134"]
                [reagent "2.0.1"]
                [re-frame "1.4.3"]]   ; <-- only in main
 :builds
 {:main-esm
  {:target :esm
   :output-dir "js-out"
   :js-options {:js-provider :import}  ; <-- critical
   :modules
   {:main {:init-fn main.core/init
           :exports {init          main.core/init
                     renderApp     main.core/render-app
                     getMuiButton  main.components/get-mui-button
                     getMuiTypography main.components/get-mui-typography
                     getMuiBox     main.components/get-mui-box
                     getMuiPaper   main.components/get-mui-paper
                     exposeGlobals main.core/expose-globals!
                     rfDispatch            main.rf/js-dispatch
                     rfDispatchSync        main.rf/js-dispatch-sync
                     rfDispatchWithPayload main.rf/js-dispatch-with-payload
                     rfSubscribe           main.rf/js-subscribe
                     rfRegEventDb          main.rf/js-reg-event-db
                     rfRegSub              main.rf/js-reg-sub}}}
   :compiler-options {:optimizations :none :pretty-print true :source-map true}}}}
```

**`:target :esm`** — produces real ES module output (native `export`/`import` statements). This is the only target compatible with Rspack's Module Federation shared singleton mechanism.

**`:js-options {:js-provider :import}`** — this single option is the linchpin. Without it, shadow-cljs inlines all npm dependencies directly into its output. With it, shadow-cljs emits native `import "react"` statements and leaves the actual module resolution to Rspack. Rspack then deduplicates those imports through Module Federation's shared scope, ensuring every app uses the exact same React instance.

**`:exports`** — ClojureScript's `:esm` target supports an `:exports` map that directly controls the ES `export let` names emitted in `main.js`. Every key must be a valid JavaScript identifier (camelCase), because hyphens are not valid in `export let` names. This is why all export names are camelCase (`renderApp`, not `render-app`).

**`re-frame "1.4.3"` only in `main`** — re-frame is a pure ClojureScript library with no npm package. It only lives in `main/shadow-cljs.edn` and gets compiled into `main`'s bundle. The remotes never see it.

### `app1/shadow-cljs.edn` (app2 is identical in structure)

```clojure
{:source-paths ["src"]
 :dependencies [[org.clojure/clojurescript "1.12.134"]
                [reagent "2.0.1"]]   ; <-- no re-frame
 :builds
 {:app1-esm
  {:target :esm
   :output-dir "js-out"
   :js-options {:js-provider :import}
   :modules
   {:app1 {:init-fn app1.core/init
           :exports {init app1.core/init
                     mount app1.core/mount}}}}}}
```

The remotes carry only `reagent` as a Clojure dependency (needed for compilation). React and react-dom are npm-only; they appear only in `devDependencies` so shadow-cljs can resolve the types at compile time, but Module Federation provides the runtime instances from `main`.

---

## 6. Rspack and Module Federation configuration

### `main/rspack.config.js` — the host

```js
const { ModuleFederationPlugin } = require("@module-federation/enhanced/rspack");

module.exports = {
  mode: "development",
  entry: "./js-out/main.js",           // shadow-cljs output is the entry
  output: {
    path: path.resolve(__dirname, "dist"),
    publicPath: "http://localhost:3001/",
    filename: "bundle.js",
  },
  resolve: {
    alias: {
      react: path.resolve(__dirname, "node_modules/react"),
      "react-dom": path.resolve(__dirname, "node_modules/react-dom"),
    },
  },
  module: {
    rules: [{ test: /\.js$/, type: "javascript/auto" }],
  },
  plugins: [
    new ModuleFederationPlugin({
      name: "mainApp",                  // NOT "main" — see section 8
      filename: "remoteEntry.js",
      exposes: {
        "./cljs-main": "./js-out/main.js",
        "./react":     "./node_modules/react/index.js",
        "./react-dom": "./node_modules/react-dom/index.js",
        "./mui-button":     "./node_modules/@mui/material/Button/index.js",
        "./mui-typography": "./node_modules/@mui/material/Typography/index.js",
        "./mui-box":        "./node_modules/@mui/material/Box/index.js",
        "./mui-paper":      "./node_modules/@mui/material/Paper/index.js",
      },
      shared: {
        react:          { singleton: true, requiredVersion: "^18.3.1", eager: true },
        "react-dom":    { singleton: true, requiredVersion: "^18.3.1", eager: true },
        "@mui/material":  { singleton: true, requiredVersion: "^5.16.7",  eager: true },
        "@emotion/react": { singleton: true, requiredVersion: "^11.13.3", eager: true },
        "@emotion/styled":{ singleton: true, requiredVersion: "^11.13.0", eager: true },
      },
    }),
  ],
};
```

**`entry: "./js-out/main.js"`** — Rspack walks the ClojureScript output, not the ClojureScript source. The ClojureScript compiler ran first.

**`type: "javascript/auto"`** — shadow-cljs emits CommonJS-style side-effect code inside what are syntactically ES modules. `javascript/auto` tells Rspack to tolerate mixed module syntax without errors.

**`eager: true` on all shared libraries** — this is exclusive to the host. `eager: true` means these modules are bundled synchronously into `bundle.js` rather than in a separate async chunk. When `bundle.js` executes, it populates `__webpack_share_scopes__.default` immediately. Without this, the shared scope would only be available after an asynchronous chunk load, and the remotes would fail to negotiate singletons.

**`resolve.alias` for react / react-dom** — forces Rspack to use only the copies in `main/node_modules`, even if Rspack's resolver finds other candidates. Prevents the rare case where two separate `react` copies end up in the bundle.

**`exposes`** — the host exposes its ClojureScript bundle and individual MUI modules. Remotes do not actually import these via Module Federation at runtime (they use `window.cljsMain` instead, see section 10), but exposing them makes them available for future direct consumption and keeps the manifest accurate.

### `app1/rspack.config.js` — a remote

```js
module.exports = {
  mode: "development",
  entry: "./js-out/app1.js",
  output: { publicPath: "http://localhost:3002/" },
  externals: [ /* cljs-runtime stub — see section 7 */ ],
  plugins: [
    new ModuleFederationPlugin({
      name: "app1",
      filename: "remoteEntry.js",
      remotes: {
        main: "mainApp@http://localhost:3001/remoteEntry.js",
      },
      exposes: { "./app1": "./js-out/app1.js" },
      shared: {
        react:          { singleton: true, requiredVersion: "^18.3.1" },
        "react-dom":    { singleton: true, requiredVersion: "^18.3.1" },
        "@mui/material":  { singleton: true, requiredVersion: "^5.16.7" },
        "@emotion/react": { singleton: true, requiredVersion: "^11.13.3" },
        "@emotion/styled":{ singleton: true, requiredVersion: "^11.13.0" },
      },
    }),
  ],
};
```

**No `eager: true` on remotes** — remotes declare the same shared libraries to enter the singleton pool, but they defer version resolution to `main`. Including `eager: true` on a remote would cause it to also bundle the libraries synchronously, defeating the entire point of sharing.

**`app2` additionally uses `import: false`** on all shared entries:

```js
shared: {
  react: { singleton: true, requiredVersion: "^18.3.1", import: false },
  // ...
}
```

`import: false` tells Rspack not to include the library in the shared scope at all from app2's side — it will only consume what main provides. This produces a slightly smaller remote bundle and explicitly communicates that app2 has no local copy to offer.

---

## 7. The cljs-runtime problem — and how externals solves it

When shadow-cljs compiles `app1`, it generates `js-out/cljs-runtime/` containing ~132 files. `app1.js` imports them. If Rspack bundles all of them into `app1`'s output, the browser executes:

1. `main`'s `bundle.js` → `goog.provide("cljs.core")` — OK
2. `app1`'s `bundle.js` → `goog.provide("cljs.core")` again → **crash**: `Namespace "cljs.core" already declared`

The fix is Rspack's `externals` option with a function resolver:

```js
externals: [
  function ({ context, request }, callback) {
    const nodePath = require("path");
    const resolved = request.startsWith(".")
      ? nodePath.join(context, request)
      : null;

    const inCljsRuntime = resolved && resolved.includes("/cljs-runtime/");
    if (!inCljsRuntime) return callback();  // not a cljs-runtime file — bundle normally

    const file = nodePath.basename(resolved);
    const isAppSpecific =
      file.startsWith("app1.") ||
      file.startsWith("shadow.module.app1.");

    if (!isAppSpecific) {
      // Stub as globalThis — already executed by main
      return callback(null, "globalThis", "global");
    }

    callback(); // app-specific namespace — bundle it
  },
],
```

**How it works:**

For every module Rspack encounters during its graph traversal, the function is called with `{ context, request }`. It resolves relative imports to absolute paths. If the resolved path sits under `cljs-runtime/` it checks the filename:

- Files like `cljs.core.js`, `goog.base.js`, `cljs_env.js`, `shadow_env.js` do **not** start with `app1.` — they are shared globals. The function returns `callback(null, "globalThis", "global")`, which emits an external reference. At runtime Rspack generates `module.exports = globalThis`, which is a no-op object. The side effects of those files have already run from `main`'s bundle.

- Files like `app1.core.js`, `app1.views.js`, `shadow.module.app1.js` **do** start with `app1.` — they are app-specific and must be bundled so their `goog.provide` and module registration calls run.

The `"global"` type (the third argument) is important: it emits a standard CommonJS external rather than an ES module external. Module Federation's chunk loader inserts `<script>` tags, not ES module `import()` calls, so the output must not use ESM external syntax.

app2's `externals` function is identical except it checks for `app2.` and `shadow.module.app2.`.

---

## 8. The container name collision — why main is named `mainApp`

Rspack's Module Federation wraps each `remoteEntry.js` in an IIFE like this:

```js
var mainApp;  // hoisted by Module Federation
(() => {
  // ... container bootstrap code ...
  mainApp = { get, init };
})();
```

If the container were named `"main"` instead, the IIFE would open with `var main;`. Inside `app1`'s bundle, ClojureScript code references `main.components`, `main.core`, etc. as property accesses on the `main` object. But inside the IIFE, `main` now refers to the hoisted `undefined` local variable, not `window.main`. Every namespace access silently returns `undefined`.

The solution is to name the host container `"mainApp"`:

```js
// main/rspack.config.js
new ModuleFederationPlugin({ name: "mainApp", ... })
```

Remotes reference it by that name:

```js
// app1/rspack.config.js
remotes: { main: "mainApp@http://localhost:3001/remoteEntry.js" }
```

The alias key (`main`) is the local name used in JS `import("main/cljs-main")` calls. The value (`mainApp@...`) is the global container name and URL. These are independent — the alias can be anything; the container name must not collide with any ClojureScript namespace prefix.

---

## 9. Sharing React, MUI and re-frame at runtime

Module Federation's shared scope mechanism works as follows:

1. `main` runs first. Because all shared libs have `eager: true`, Rspack bootstraps them synchronously into `__webpack_share_scopes__.default` when `bundle.js` executes.
2. `index.html` then dynamically loads `app1/remoteEntry.js` and `app2/remoteEntry.js` via `<script>` tags.
3. For each remote the host calls `app1Container.init(__webpack_share_scopes__.default)`, passing in the already-populated scope.
4. When app1 requests `react` or `@mui/material` (either via an explicit JS import or via ClojureScript's reagent wrappers), Module Federation resolves the request to `main`'s already-initialised singleton instance. Nothing is downloaded twice.

**re-frame is different.** It is a pure ClojureScript library compiled into `main.js`. There is no npm package for it, no `node_modules/re-frame`, and nothing to put in the `shared` section of any Rspack config. Remotes access re-frame's functionality entirely through the `window.cljsMain` bridge (section 10).

---

## 10. The `window.cljsMain` bridge

When `main.core/init` runs, it calls `expose-globals!`, which populates `window.cljsMain`:

```clojure
(defn expose-globals! []
  (set! (.-cljsMain js/window)
        #js {:getMuiButton     components/mui-button
             :getMuiTypography components/mui-typography
             :getMuiBox        components/mui-box
             :getMuiPaper      components/mui-paper
             :rfDispatch            rf/js-dispatch
             :rfDispatchSync        rf/js-dispatch-sync
             :rfDispatchWithPayload rf/js-dispatch-with-payload
             :rfSubscribe           rf/js-subscribe
             :rfRegEventDb          rf/js-reg-event-db
             :rfRegSub              rf/js-reg-sub}))
```

Remotes read `window.cljsMain` in their `shared.cljs`:

```clojure
(defn- mf-main-exports []
  (when-let [exports (.-cljsMain js/window)]
    exports))

(defn get-button []
  (when-let [m (mf-main-exports)]
    (.-getMuiButton m)))
```

**Why `window.cljsMain` instead of a Module Federation `import("main/cljs-main")`?**

A static MF virtual module import would require shadow-cljs to resolve `"main/cljs-main"` at compile time — but that module only exists in Rspack's runtime, not on disk. ClojureScript's compiler would fail. Using `window.cljsMain` defers the lookup entirely to runtime, after all MF containers have been initialised.

**Why not use `[:> component props]` for components from `window.cljsMain`?**

The values stored on `window.cljsMain` are ClojureScript functions (reagent component wrappers). Reagent's `[:>]` syntax is for raw JavaScript React components — it passes arguments through `js/React.createElement` without hiccup conversion. If you pass a ClojureScript function through `[:>]`, reagent emits keyword objects as React children, which crash at render time. Use the vector form instead:

```clojure
; correct
(let [btn (shared/get-button)]
  [btn {:variant "contained"} "Click me"])

; wrong — crashes
(let [btn (shared/get-button)]
  [:> btn {:variant "contained"} "Click me"])
```

---

## 11. The re-frame bridge

`main/src/main/rf.cljs` owns the entire app-db and registers all events and subscriptions:

```clojure
(def default-db
  {:counter {:count 0}
   :todos   {:items [...] :next-id 4 :input ""}})

(rf/reg-event-db :counter/increment
  (fn [db _] (update-in db [:counter :count] inc)))

(rf/reg-sub :counter/count
  (fn [db _] (get-in db [:counter :count])))
```

The JS-callable bridge functions convert between JavaScript and ClojureScript representations:

```clojure
(defn ^:export js-dispatch [js-vec]
  (let [clj-vec  (js->clj js-vec)
        event-kw (keyword (first clj-vec))
        args     (rest clj-vec)]
    (rf/dispatch (into [event-kw] args))))

(defn ^:export js-subscribe [query-kw-str]
  (rf/subscribe [(keyword query-kw-str)]))
```

Each remote wraps these in a thin `rf.cljs` that mirrors re-frame's own API:

```clojure
; app1/src/app1/rf.cljs
(defn dispatch [event-vec]
  (when-let [b (.-cljsMain js/window)]
    ((.-rfDispatch b)
     (clj->js (mapv #(if (keyword? %) (kw->str %) %) event-vec)))))

(defn subscribe [query-vec]
  (when-let [b (.-cljsMain js/window)]
    ((.-rfSubscribe b) (kw->str (first query-vec)))))
```

Remote views use these exactly as they would use re-frame directly:

```clojure
(defn counter-widget []
  (let [count (rf/subscribe [:counter/count])]
    (fn []
      [:div
       (str "Count: " @count)
       [:button {:on-click #(rf/dispatch [:counter/increment])} "+"]
       [:button {:on-click #(rf/dispatch [:counter/decrement])} "-"]])))
```

There is exactly one re-frame app-db, initialised once by `main`, shared with all remotes through `window.cljsMain`. Subscriptions return real reagent reactions — remotes deref them with `@` inside render functions exactly as normal.

---

## 12. `index.html` — bootstrapping order matters

```html
<!-- 1. Load main's MF container bootstrap -->
<script src="/remoteEntry.js"></script>

<!-- 2. Load main's application code (ClojureScript + React + MUI)
        This runs init(), which calls rf/init! and expose-globals!
        After this line, window.cljsMain is populated. -->
<script src="/bundle.js"></script>

<script type="module">
  async function loadRemotes() {
    // 3. Load remote containers
    await loadScript('http://localhost:3002/remoteEntry.js');
    await loadScript('http://localhost:3003/remoteEntry.js');

    // 4. Pass main's already-populated shared scope to remotes
    const sharedScope = __webpack_share_scopes__.default;

    // 5. Initialise and mount each remote
    await window.app1.init(sharedScope);
    const app1 = (await window.app1.get('./app1'))();
    app1.mount('app1-root');

    await window.app2.init(sharedScope);
    const app2 = (await window.app2.get('./app2'))();
    app2.mount('app2-root');
  }
  loadRemotes();
</script>
```

**Why `remoteEntry.js` before `bundle.js`?**

`remoteEntry.js` registers the Module Federation container and sets up the shared scope infrastructure. `bundle.js` then runs the ClojureScript application code, which calls `expose-globals!`. If `bundle.js` loaded first, the MF runtime would not be in place when `init()` ran.

**Why pass `__webpack_share_scopes__.default` instead of calling `.init()` without arguments?**

When `main`'s `bundle.js` executed, `eager: true` already bootstrapped all shared modules into `__webpack_share_scopes__.default`. Calling `app1Container.init(sharedScope)` injects that pre-populated scope into app1's MF runtime. App1 then resolves `react`, `@mui/material`, etc. against the instances already in memory — no duplicate downloads, no duplicate instances.

**Why `await` between remote loads?**

The remotes are loaded sequentially to avoid race conditions in scope initialisation. In production you could parallelise the `loadScript` calls and then initialise each container after all scripts have loaded.

---

## 13. Production build and bundle analysis

Run the production build in sequence (main first, remotes can be parallel):

```bash
# main — must go first
cd main && npx shadow-cljs compile main-esm && npx @rspack/cli build --mode production

# remotes — can run in parallel after main
cd app1 && npx shadow-cljs compile app1-esm && npx @rspack/cli build --mode production
cd app2 && npx shadow-cljs compile app2-esm && npx @rspack/cli build --mode production
```

### `main/dist/` — what Rspack emits

| File | Raw | Gzip | Contents |
|------|-----|------|----------|
| `bundle.js` | 1.7 MB | **299 KB** | Entry point — React 18, react-dom, emotion, @mui/material, ClojureScript application code, re-frame, reagent. Everything `eager: true`. |
| `remoteEntry.js` | 265 KB | **84 KB** | Module Federation container bootstrap. Registers the shared scope and exposes the MUI modules and `cljs-main`. |
| `501.bundle.js` | 1.4 MB | **199 KB** | ClojureScript runtime — Google Closure Library, `cljs.core`, reagent internals. Lazy chunk loaded when the `./cljs-main` exposed module is consumed. |
| `245.bundle.js` | 28 KB | 10 KB | MUI shared utilities (emotion styling, prop-types). |
| `439.bundle.js` | 20 KB | 6.7 KB | MUI `ButtonBase` and `ButtonGroup` internals. |
| `515.bundle.js` | 7.2 KB | 2.9 KB | MUI `Typography` component. |
| `361.bundle.js` | 2.2 KB | 1.1 KB | MUI `Paper` component. |
| `198.bundle.js` | 2.3 KB | 1.2 KB | MUI `Box` component. |
| `570.bundle.js` | 1.0 KB | 627 B | MUI utility shim. |

The 1.7 MB `bundle.js` is large but expected — it carries React + MUI + the full ClojureScript runtime. On the wire with gzip it transfers as **299 KB**. This is paid once; remotes do not re-download any of it.

The large `501.bundle.js` (1.4 MB raw / **199 KB** gzip) contains the ClojureScript/Google Closure runtime that shadow-cljs emits for `cljs.core` and reagent. This is also a one-time cost.

### `app1/dist/` and `app2/dist/`

| File | Raw | Gzip | Contents |
|------|-----|------|----------|
| `bundle.js` | 105 KB | **30 KB** | MF bootstrap + app1 ClojureScript code. No React, no MUI. |
| `remoteEntry.js` | 98 KB | **29 KB** | MF remote entry — registers app1 container, wires shared scope. |
| `991.bundle.js` | 11 KB | **2 KB** | App1-specific ClojureScript: `app1.core`, `app1.views`, `app1.shared`, `app1.rf` — the actual application logic. |

| File | Raw | Gzip | Contents |
|------|-----|------|----------|
| `bundle.js` | 110 KB | **31 KB** | Same structure as app1. |
| `remoteEntry.js` | 98 KB | **29 KB** | App2 MF remote entry. |
| `586.bundle.js` | 16 KB | **3 KB** | App2-specific ClojureScript — `app2.core`, `app2.views`, `app2.shared`, `app2.rf`. |

**The key number:** the actual unique application logic in each remote is **2–3 KB gzip**. The `bundle.js` and `remoteEntry.js` files are almost entirely Module Federation plumbing, not application code.

### What the browser loads

On a cold page load the browser fetches:

| Source | Files | Transfer (gzip) |
|--------|-------|-----------------|
| main | `remoteEntry.js` + `bundle.js` | ~383 KB |
| app1 | `remoteEntry.js` + `bundle.js` + `991.bundle.js` | ~61 KB |
| app2 | `remoteEntry.js` + `bundle.js` + `586.bundle.js` | ~63 KB |
| MUI lazy chunks | 6 files from main | ~23 KB |
| ClojureScript runtime | `501.bundle.js` | ~199 KB |
| **Total** | | **~729 KB** |

The 6 MUI chunks come from `main`'s origin (port 3001), so browsers can pipeline them with the main bundle download. They are never re-fetched for subsequent remote apps.

### Build times

| Phase | App | Time |
|-------|-----|------|
| shadow-cljs compile | main | ~13 s |
| shadow-cljs compile | app1 | ~12 s |
| shadow-cljs compile | app2 | ~12 s |
| rspack build (prod) | main | ~2.2 s |
| rspack build (prod) | app1 | ~278 ms |
| rspack build (prod) | app2 | ~355 ms |

Rspack is significantly faster than webpack for the bundling step; the dominant cost is the JVM startup and ClojureScript compilation via shadow-cljs.

---

## 14. Key rules summarised

| # | Rule | Why |
|---|------|-----|
| 1 | `main` owns all npm `dependencies`; remotes use only `devDependencies` | Prevents duplicate runtime copies |
| 2 | `app1`/`app2` have no new Clojure or npm dependencies at runtime | They consume everything from `main` |
| 3 | Always use `:js-provider :import` in shadow-cljs | Lets Rspack deduplicate imports via Module Federation shared scope |
| 4 | `:exports` map keys must be camelCase | They become `export let <name>` — hyphens are invalid JS identifiers |
| 5 | Components from `window.cljsMain` use `[comp props]`, not `[:> comp props]` | `[:>]` is for raw JS components; ClojureScript wrappers need the vector form |
| 6 | re-frame never appears in any `rspack.config.js` `shared` section | It has no npm package; it is a ClojureScript-only library |
| 7 | Remotes stub `cljs-runtime/` non-app-specific files as `globalThis` externals | Prevents double execution of goog bootstrap and `cljs.core` |
| 8 | The host MF container name must not match any ClojureScript namespace prefix | Rspack's IIFE hoists `var <name>` which shadows the global namespace |
| 9 | `main` must start before remotes; `expose-globals!` must run before any remote mounts | Remotes read `window.cljsMain` on first render — it must already exist |
| 10 | `eager: true` on shared libs in `main` only | Forces synchronous shared scope population so remotes can consume singletons without async chunks |
| 11 | Pass `__webpack_share_scopes__.default` to remote `.init()` | Injects main's pre-populated scope so remotes receive the same React/MUI instances |
| 12 | `index.html` loads `remoteEntry.js` then `bundle.js` | MF container must be registered before application code runs |
