(ns kee-frame.core
  (:require [kee-frame.state :as state]
            [kee-frame.router :as router]))

(defn start! [routes initial-db]
  (router/start! routes initial-db))

(defn reg-controller [id controller]
  (swap! state/controllers assoc id controller))

(defn path-for [handler & params]
  (apply router/url handler params))