(ns kee-frame.core
  (:require [kee-frame.state :as state]
            [re-frame.core :as rf]
            [kee-frame.router :as router]))

(defn start! [routes initial-db]
  (router/start! routes initial-db))

(defn reg-controller [id controller]
  (swap! state/controllers assoc id controller))

(defn reg-swap [id f & args]
  (rf/reg-event-db id [rf/debug] (fn [db] (apply f db args))))

(defn path-for [handler & params]
  (apply router/url handler params))