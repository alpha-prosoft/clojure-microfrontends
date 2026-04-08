(ns app1.rf
  "re-frame bridge for app1.

   Delegates all re-frame calls to main's shared instance via
   window.cljsMain — app1 carries no re-frame dependency of its own.

   Usage:
     (rf/dispatch [:counter/increment])
     @(rf/subscribe [:counter/count])")

(defn- rf-bridge []
  (.-cljsMain js/window))

(defn- kw->str
  "Convert a keyword to its fully-qualified string form.
   :counter/count  → \"counter/count\"
   :increment      → \"increment\""
  [kw]
  (if (namespace kw)
    (str (namespace kw) "/" (name kw))
    (name kw)))

(defn dispatch
  "Dispatch a re-frame event vector, e.g. (dispatch [:counter/increment])."
  [event-vec]
  (when-let [b (rf-bridge)]
    (let [f (.-rfDispatch b)]
      (f (clj->js (mapv (fn [x] (if (keyword? x) (kw->str x) x)) event-vec))))))

(defn dispatch-sync
  "Synchronously dispatch a re-frame event vector."
  [event-vec]
  (when-let [b (rf-bridge)]
    (let [f (.-rfDispatchSync b)]
      (f (clj->js (mapv (fn [x] (if (keyword? x) (kw->str x) x)) event-vec))))))

(defn dispatch-with-payload
  "Dispatch an event that carries a non-keyword payload.
   e.g. (dispatch-with-payload :todos/set-input \"hello\")"
  [event-kw payload]
  (when-let [b (rf-bridge)]
    (let [f (.-rfDispatchWithPayload b)]
      (f (kw->str event-kw) payload))))

(defn subscribe
  "Subscribe to a re-frame query. Returns a reagent reaction.
   Deref it with @ inside a reagent render function to get the current value.
   e.g. @(rf/subscribe [:counter/count])"
  [query-vec]
  (when-let [b (rf-bridge)]
    (let [f (.-rfSubscribe b)]
      (f (kw->str (first query-vec))))))
