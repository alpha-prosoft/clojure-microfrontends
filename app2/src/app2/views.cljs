(ns app2.views
  (:require [app2.shared :as shared]
            [app2.rf :as rf]))

(defn todo-item [{:keys [id text done?]}]
  (let [typo (shared/get-typography)]
    [:div {:style {:display "flex"
                   :align-items "center"
                   :gap "0.5rem"
                   :padding "0.25rem 0"
                   :text-decoration (when done? "line-through")
                   :opacity (if done? "0.5" "1")}}
     [:input {:type "checkbox"
              :checked done?
              :on-change #(rf/dispatch [:todos/toggle id])
              :style {:cursor "pointer"}}]
     [typo {:variant "body1" :style {:flex 1}} text]
     [:button {:on-click #(rf/dispatch [:todos/delete id])
               :style {:border "none"
                       :background "transparent"
                       :cursor "pointer"
                       :color "red"
                       :font-size "1.2rem"}}
      "×"]]))

(defn root []
  (let [todos (rf/subscribe [:todos/items])
        input (rf/subscribe [:todos/input])
        btn   (shared/get-button)
        typo  (shared/get-typography)
        paper (shared/get-paper)]
    (fn []
      [:div {:style {:padding "1rem"}}
       [typo {:variant "h5" :color "secondary.main"} "App 2 - Todo List"]
       [typo {:variant "body2" :style {:margin-bottom "1rem" :color "gray"}}
        "React, MUI and reagent loaded from main via Module Federation"]
       [paper {:elevation 2 :style {:padding "1rem" :margin-bottom "1rem"}}
        [:div {:style {:display "flex" :gap "0.5rem" :margin-bottom "1rem"}}
         [:input {:value (or @input "")
                  :on-change #(rf/dispatch-with-payload :todos/set-input (.. % -target -value))
                  :on-key-down #(when (= (.-key %) "Enter")
                                  (rf/dispatch [:todos/add]))
                  :placeholder "Add todo and press Enter..."
                  :style {:flex 1
                          :padding "0.5rem"
                          :border "1px solid #ccc"
                          :border-radius "4px"
                          :font-size "1rem"}}]
         [btn {:variant "contained"
               :color "secondary"
               :on-click #(rf/dispatch [:todos/add])}
          "Add"]]
        [:div
         (for [{:keys [id] :as todo} (or @todos [])]
           ^{:key id}
           [todo-item todo])]]])))
