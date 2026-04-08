(ns app1.views
  (:require [app1.shared :as shared]
            [app1.rf :as rf]))

(defn counter-widget []
  (let [count (rf/subscribe [:counter/count])
        btn   (shared/get-button)
        typo  (shared/get-typography)]
    (fn []
      [:div {:style {:display "flex" :flex-direction "column" :gap "0.5rem"}}
       [typo {:variant "subtitle1"} (str "Count: " @count)]
       [:div {:style {:display "flex" :gap "0.5rem"}}
        [btn {:variant "outlined" :color "success"
              :on-click #(rf/dispatch [:counter/increment])} "Increment"]
        [btn {:variant "outlined" :color "error"
              :on-click #(rf/dispatch [:counter/decrement])} "Decrement"]
        [btn {:variant "text"
              :on-click #(rf/dispatch [:counter/reset])} "Reset"]]])))

(defn root []
  (let [typo (shared/get-typography)]
    [:div {:style {:padding "1rem"}}
     [typo {:variant "h5" :color "success.main"} "App 1 - Counter"]
     [typo {:variant "body2" :style {:margin-bottom "1rem" :color "gray"}}
      "React, MUI and reagent loaded from main via Module Federation"]
     [counter-widget]]))
