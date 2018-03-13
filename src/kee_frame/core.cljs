(ns kee-frame.core
  (:require [kee-frame.state :as state]
            [kee-frame.router :as router]
            [kee-frame.chain :as chain]
            [re-frame.core :as rf]
            [kee-frame.spec :as spec]
            [kee-frame.spec :refer [spec-interceptor]]
            [kee-frame.debug :refer [debug-interceptor]]
            [clojure.spec.alpha :as s]))

(def interceptors [(spec-interceptor state/app-db-spec) (debug-interceptor state/debug?) rf/trim-v])

(defn start! [options]
  (router/start! (assoc options :interceptors interceptors)))


(defn reg-controller [id controller]
  (when-not (s/valid? ::spec/controller controller)
    (throw (ex-info "Controller is not valid" (s/explain-data ::spec/controller controller))))
  (swap! state/controllers assoc id controller))

(s/fdef reg-controller
        :args (s/cat :id keyword?
                     :controller ::controller))


(defn reg-event-fx [id handler]
  (rf/reg-event-fx id interceptors handler))

(defn reg-event-db [id handler]
  (rf/reg-event-db id interceptors handler))

(defn reg-chain [id & handlers]
  (apply chain/reg-chain id interceptors handlers))

(defn path-for [handler & params]
  (apply router/url handler params))