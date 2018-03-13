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
    (throw (ex-info "Invalid controller" (s/explain-data ::spec/controller controller))))
  (swap! state/controllers assoc id controller))

(s/fdef reg-controller
        :args (s/cat :id keyword?
                     :controller ::spec/controller))


(defn reg-event-fx [id handler]
  (rf/reg-event-fx id interceptors handler))

(defn reg-event-db [id handler]
  (rf/reg-event-db id interceptors handler))

(defn reg-chain [id & handlers]
  (when-not (s/valid? ::spec/chain-handlers handlers)
    (throw (ex-info "Invalid chain" (s/explain-data ::spec/chain-handlers handlers))))
  (apply chain/reg-chain id interceptors handlers))

(s/fdef reg-chain
        :args (s/cat :id keyword?
                     :chain-handlers ::spec/chain-handlers))

(defn path-for [handler & params]
  (apply router/url handler params))