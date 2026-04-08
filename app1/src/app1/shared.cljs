(ns app1.shared
  "Accessor functions for components provided by main via Module Federation.
   We use raw JS interop to access the __mf_container at runtime rather than
   a static ClojureScript require, since 'main/cljs-main' is a virtual MF
   module that only exists at Rspack runtime, not at ClojureScript compile time.")

;; main's remoteEntry.js registers a global window.__mf_main container,
;; or we can access the MF-shared cljs-main module via the MF runtime API.
;; The cleanest approach: main exposes JS globals via its bootstrap script.

(defn- mf-main-exports []
  ;; Access the module exposed by main MF container.
  ;; At runtime, after MF wires everything up, the shared cljs-main module
  ;; is available. We look it up via the global __mf_modules registry or
  ;; use the window-attached exports that main's bootstrap sets up.
  (when-let [exports (.-cljsMain js/window)]
    exports))

(defn get-button []
  (when-let [m (mf-main-exports)]
    (.-getMuiButton m)))

(defn get-typography []
  (when-let [m (mf-main-exports)]
    (.-getMuiTypography m)))

(defn get-box []
  (when-let [m (mf-main-exports)]
    (.-getMuiBox m)))
