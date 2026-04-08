(ns main.rf
  "re-frame bridge for the microfrontend host.

   This namespace owns the re-frame app-db and registers all shared
   events and subscriptions.  It also provides JS-callable wrapper
   functions that are attached to window.cljsMain by expose-globals!
   so that remote micro-frontends (app1, app2) can use re-frame
   without carrying their own copy of the library."
  (:require [re-frame.core :as rf]))

;; ---------------------------------------------------------------------------
;; Initial state
;; ---------------------------------------------------------------------------

(def default-db
  {:counter {:count 0}
   :todos   {:items  [{:id 1 :text "Learn ClojureScript"          :done? true}
                      {:id 2 :text "Set up Module Federation"     :done? false}
                      {:id 3 :text "Build awesome microfrontends" :done? false}]
             :next-id 4
             :input   ""}})

;; ---------------------------------------------------------------------------
;; Events — counter
;; ---------------------------------------------------------------------------

(rf/reg-event-db
 :counter/increment
 (fn [db _] (update-in db [:counter :count] inc)))

(rf/reg-event-db
 :counter/decrement
 (fn [db _] (update-in db [:counter :count] dec)))

(rf/reg-event-db
 :counter/reset
 (fn [db _] (assoc-in db [:counter :count] 0)))

;; ---------------------------------------------------------------------------
;; Events — todos
;; ---------------------------------------------------------------------------

(rf/reg-event-db
 :todos/set-input
 (fn [db [_ value]]
   (assoc-in db [:todos :input] value)))

(rf/reg-event-db
 :todos/add
 (fn [db _]
   (let [text (clojure.string/trim (get-in db [:todos :input]))]
     (if (seq text)
       (-> db
           (update-in [:todos :items] conj {:id     (get-in db [:todos :next-id])
                                            :text   text
                                            :done?  false})
           (update-in [:todos :next-id] inc)
           (assoc-in  [:todos :input] ""))
       db))))

(rf/reg-event-db
 :todos/toggle
 (fn [db [_ id]]
   (update-in db [:todos :items]
              (fn [items]
                (mapv (fn [t] (if (= (:id t) id) (update t :done? not) t))
                      items)))))

(rf/reg-event-db
 :todos/delete
 (fn [db [_ id]]
   (update-in db [:todos :items]
              (fn [items] (filterv #(not= (:id %) id) items)))))

;; ---------------------------------------------------------------------------
;; Subscriptions
;; ---------------------------------------------------------------------------

(rf/reg-sub :counter/count   (fn [db _] (get-in db [:counter :count])))
(rf/reg-sub :todos/items     (fn [db _] (get-in db [:todos :items])))
(rf/reg-sub :todos/input     (fn [db _] (get-in db [:todos :input])))

;; ---------------------------------------------------------------------------
;; Initialisation
;; ---------------------------------------------------------------------------

(defn init!
  "Initialise the re-frame app-db with default-db.
   Call once from main.core/init before mounting any React tree."
  []
  (rf/dispatch-sync [:initialise-db]))

(rf/reg-event-db
 :initialise-db
 (fn [_ _] default-db))

;; ---------------------------------------------------------------------------
;; JS-callable wrappers exposed via window.cljsMain
;;
;; All functions below accept / return plain JS values so they can be
;; called from app1 / app2 without those apps importing re-frame directly.
;; ---------------------------------------------------------------------------

(defn ^:export js-dispatch
  "Dispatch a re-frame event from a JS array: js-dispatch(['event-id', ...args])
   Only the first element (event id) is keywordized; remaining args are passed as-is."
  [js-vec]
  (let [clj-vec (js->clj js-vec)
        event-kw (keyword (first clj-vec))
        args     (rest clj-vec)]
    (rf/dispatch (into [event-kw] args))))

(defn ^:export js-dispatch-sync
  "Synchronously dispatch a re-frame event from a JS array.
   Only the first element (event id) is keywordized; remaining args are passed as-is."
  [js-vec]
  (let [clj-vec (js->clj js-vec)
        event-kw (keyword (first clj-vec))
        args     (rest clj-vec)]
    (rf/dispatch-sync (into [event-kw] args))))

(defn ^:export js-dispatch-with-payload
  "Dispatch an event that carries a non-keyword payload.
   js-dispatch-with-payload(':todos/set-input', 'hello')"
  [event-kw-str payload]
  (rf/dispatch [(keyword event-kw-str) payload]))

(defn ^:export js-subscribe
  "Subscribe to a re-frame query and return the reagent reaction.
   The caller must deref it (@reaction) inside a reagent render function."
  [query-kw-str]
  (rf/subscribe [(keyword query-kw-str)]))

(defn ^:export js-reg-event-db
  "Register a re-frame :event-db handler from JS.
   handler-fn receives (db, event-vec) as ClojureScript values."
  [event-kw-str handler-fn]
  (rf/reg-event-db (keyword event-kw-str)
                   (fn [db event] (handler-fn db (clj->js event)))))

(defn ^:export js-reg-sub
  "Register a re-frame subscription from JS.
   extractor-fn receives the app-db as a plain JS object."
  [query-kw-str extractor-fn]
  (rf/reg-sub (keyword query-kw-str)
              (fn [db _] (extractor-fn (clj->js db)))))
