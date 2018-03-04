(ns kee-frame.core
  (:require [kee-frame.state :as state]
            [re-frame.core :as rf]
            [kee-frame.controller :as controller]
            [bidi.bidi :as bidi]))

(defn start! [routes]
  (controller/start-router! routes))

(rf/reg-sub :route (fn [db] (:route db)))

(defn reg-controller [id controller]
  (swap! state/controllers assoc id controller))

(defn path-for [handler & params]
  (apply bidi/path-for @state/routes handler params))
