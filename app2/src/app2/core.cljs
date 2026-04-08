(ns app2.core
  (:require [reagent.core :as reagent]
            [reagent.dom.client :as rdom]
            [app2.views :as views]))

(defonce root (atom nil))

(defn mount
  "Mount app2 into the given DOM element id."
  [element-id]
  (let [el (.getElementById js/document element-id)]
    (when el
      (let [^js r (or @root (rdom/create-root el))]
        (reset! root r)
        (.render r (reagent/as-element [views/root]))))))

(defn init []
  (mount "app2-root"))
