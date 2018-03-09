(ns kee-frame.core
  (:require [kee-frame.state :as state]
            [kee-frame.router :as router]))

(defn start! [options]
  (router/start! options))

(defn reg-controller [id controller]
  (swap! state/controllers assoc id controller))

(defn path-for [handler & params]
  (apply router/url handler params))