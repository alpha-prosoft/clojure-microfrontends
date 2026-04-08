(ns app2.shared
  "Accessor functions for components provided by main via Module Federation.")

(defn- mf-main-exports []
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

(defn get-paper []
  (when-let [m (mf-main-exports)]
    (.-getMuiPaper m)))
