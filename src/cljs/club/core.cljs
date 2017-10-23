(ns club.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-frame.db :refer [app-db]]
            [goog.events :as events]
            [club.events]
            [club.subs]
            [club.views :as views]
            [club.config :as config])
  (:import  [goog History]
            [goog.history EventType]))

(defn dev-setup []
  (when (or true config/debug?)
    (enable-console-print!)
    (println "dev mode")))

; Install the navigation: listen to NAVIGATE events and dispatch to :nav
(def history
  (doto (History.)
    (events/listen EventType.NAVIGATE
                   (fn [event] (re-frame/dispatch [:nav (.-token event)])))
    (.setEnabled true)))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (re-frame/dispatch-sync [:initialize-db])
  (dev-setup)
  (mount-root))

; Test app-db at loading time
(if (club.events/check-and-throw :club.db/db @app-db)
  (.log js/console "DB ok"))
