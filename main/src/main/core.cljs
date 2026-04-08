(ns main.core
  (:require [reagent.core :as reagent]
            [reagent.dom.client :as rdom]
            [main.components :as components]
            [main.rf :as rf]))

(defn app []
  [:div {:style {:padding "2rem" :font-family "sans-serif"}}
   [:h1 {:style {:color "#1976d2"}} "Main App (Host)"]
   [:p "This is the main application. It provides shared dependencies to app1 and app2."]
   [:div {:style {:margin-top "1rem"}}
    [components/mui-button {:variant "contained" :color "primary"} "Main App Button"]
    [:div {:style {:margin-top "1rem"}}
     [components/mui-typography {:variant "h6"} "Shared MUI Typography from Main"]]]])

(defonce root (atom nil))

(defn render-app
  "Mounts the main React app into the given DOM element id."
  [element-id]
  (let [el (.getElementById js/document element-id)]
    (when el
      (let [^js r (or @root (rdom/create-root el))]
        (reset! root r)
        (.render r (reagent/as-element [app]))))))

(defn ^:export expose-globals!
  "Attach shared component factories and re-frame bridge functions to
   window.cljsMain so that remote micro-frontends (app1, app2) can
   access them at runtime without a static JS import of a Module
   Federation virtual module."
  []
  (set! (.-cljsMain js/window)
        #js {;; MUI component factories
             :getMuiButton     components/mui-button
             :getMuiTypography components/mui-typography
             :getMuiBox        components/mui-box
             :getMuiPaper      components/mui-paper
             ;; re-frame bridge
             ;; Dispatch: dispatch(["event-id", ...args])
             :rfDispatch            rf/js-dispatch
             ;; Synchronous dispatch
             :rfDispatchSync        rf/js-dispatch-sync
             ;; Dispatch with arbitrary payload: rfDispatchWithPayload(":event/id", payload)
             :rfDispatchWithPayload rf/js-dispatch-with-payload
             ;; Subscribe: returns a reagent reaction — deref inside render fn
             :rfSubscribe           rf/js-subscribe
             ;; Advanced: register new events / subs from a remote app
             :rfRegEventDb          rf/js-reg-event-db
             :rfRegSub              rf/js-reg-sub}))

(defn init []
  (rf/init!)
  (expose-globals!)
  (render-app "app"))

(defn ^:export get-version []
  "1.0.0")
