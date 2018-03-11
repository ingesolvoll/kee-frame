(ns kee-frame.core
  (:require [kee-frame.state :as state]
            [kee-frame.router :as router]
            [kee-frame.chain :as chain]
            [re-frame.core :as rf]
            [kee-frame.spec :refer [spec-interceptor]]
            [kee-frame.debug :refer [debug-interceptor]]))

(def interceptors [(spec-interceptor state/app-db-spec) (debug-interceptor state/debug?)])

(defn start! [options]
  (router/start! (assoc options :interceptors interceptors)))

(defn reg-controller [id controller]
  (swap! state/controllers assoc id controller))

(defn reg-event-fx [id handler]
  (rf/reg-event-fx id interceptors handler))

(defn reg-event-db [id handler]
  (rf/reg-event-db id interceptors handler))

(defn reg-chain [id & handlers]
  (apply chain/reg-chain id interceptors handlers))

(defn path-for [handler & params]
  (apply router/url handler params))