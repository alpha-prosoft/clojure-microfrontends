(ns main.components
  (:require ["@mui/material/Button$default" :as MuiButton]
            ["@mui/material/Typography$default" :as MuiTypography]
            ["@mui/material/Box$default" :as MuiBox]
            ["@mui/material/Paper$default" :as MuiPaper]
            [reagent.core :as r]))

;; Wrap MUI components as reagent components

(defn mui-button
  [props & children]
  (into [:> MuiButton props] children))

(defn mui-typography
  [props & children]
  (into [:> MuiTypography props] children))

(defn mui-box
  [props & children]
  (into [:> MuiBox props] children))

(defn mui-paper
  [props & children]
  (into [:> MuiPaper props] children))

;; Export factory functions for use by remote apps
;; These return reagent component constructors

(defn ^:export get-mui-button []
  mui-button)

(defn ^:export get-mui-typography []
  mui-typography)

(defn ^:export get-mui-box []
  mui-box)

(defn ^:export get-mui-paper []
  mui-paper)
