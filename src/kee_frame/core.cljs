(ns kee-frame.core
  (:require [kee-frame.state :as state]
            [kee-frame.router :as router]
            [kee-frame.chain :as chain]
            [re-frame.core :as rf]
            [kee-frame.spec :refer [spec-interceptor]]))

(defn start! [options]
  (router/start! options))

(defn reg-controller [id controller]
  (swap! state/controllers assoc id controller))

(def interceptors [rf/debug (spec-interceptor state/app-db-spec)])

(defn reg-event-fx [id handler]
  (rf/reg-event-fx id interceptors handler))

(defn reg-event-db [id handler]
  (rf/reg-event-db id interceptors handler))

(defn reg-chain [id & handlers]
  (apply chain/reg-chain id interceptors handlers))

(defn path-for [handler & params]
  (apply router/url handler params))