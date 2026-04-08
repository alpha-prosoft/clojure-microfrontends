(ns app2.rf
  "re-frame bridge for app2.

   Delegates all re-frame calls to main's shared instance via
   window.cljsMain — app2 carries no re-frame dependency of its own.

   Usage:
     (rf/dispatch [:todos/add])
     (rf/dispatch-with-payload :todos/set-input \"hello\")
     @(rf/subscribe [:todos/items])")

(defn- rf-bridge []
  (.-cljsMain js/window))

(defn- kw->str
  "Convert a keyword to its fully-qualified string form.
   :todos/items → \"todos/items\"
   :add         → \"add\""
  [kw]
  (if (namespace kw)
    (str (namespace kw) "/" (name kw))
    (name kw)))

(defn dispatch
  "Dispatch a re-frame event vector, e.g. (dispatch [:todos/add])."
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
   e.g. @(rf/subscribe [:todos/items])"
  [query-vec]
  (when-let [b (rf-bridge)]
    (let [f (.-rfSubscribe b)]
      (f (kw->str (first query-vec))))))
