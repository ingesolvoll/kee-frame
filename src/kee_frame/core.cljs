(ns kee-frame.core
  (:require [kee-frame.state :as state]
            [kee-frame.router :as router]
            [kee-frame.chain :as chain]))

(defn start! [options]
  (router/start! options))

(defn reg-controller [id controller]
  (swap! state/controllers assoc id controller))

(defn reg-chain [id & handlers]
  (apply chain/reg-chain-2 id handlers))

(defn path-for [handler & params]
  (apply router/url handler params))