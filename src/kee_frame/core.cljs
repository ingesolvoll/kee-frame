(ns kee-frame.core
  (:require [kee-frame.state :as state]
            [re-frame.core :as rf]
            [kee-frame.router :as router]))

(defn start! [routes]
  (router/start! routes))

(defn reg-controller [id controller]
  (swap! state/controllers assoc id controller))

(defn reg-swap [id f & args]
  (rf/reg-event-db id (fn [db] (apply f db args))))

(defn path-for [handler & params]
  (apply router/url handler params))