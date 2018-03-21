(ns kee-frame.core
  (:require [kee-frame.state :as state]
            [kee-frame.router :as router]
            [kee-frame.chain :as chain]
            [re-frame.core :as rf]
            [kee-frame.spec :as spec]
            [kee-frame.spec :refer [spec-interceptor]]
            [kee-frame.debug :refer [debug-interceptor]]
            [re-frame.core :refer [console]]
            [clojure.spec.alpha :as s]))

(def interceptors [(spec-interceptor state/app-db-spec) (debug-interceptor state/debug?) rf/trim-v])

(def valid-option-key? #{:routes :process-route :debug? :app-db-spec :root-component :initial-db})

(defn extra-options [options]
  (->> options
       (filter (fn [[k]] (not (valid-option-key? k))))
       (into {})))

(defn start! [options]
  (when-not (s/valid? ::spec/start-options options)
    (throw (ex-info "Invalid options" (s/explain-data ::spec/start-options options))))
  (let [extras (extra-options options)]
    (when (seq extras)
      (throw (ex-info (str "Uknown startup options. Valid keys are " valid-option-key?) extras))))
  (router/start! (assoc options :interceptors interceptors)))

(defn reg-controller [id controller]
  (when-not (s/valid? ::spec/controller controller)
    (throw (ex-info "Invalid controller" (s/explain-data ::spec/controller controller))))
  (when (get @state/controllers id)
    (console :warn "Overwriting controller with id " id))
  (swap! state/controllers update id merge controller))

(defn reg-event-fx [id handler]
  (rf/reg-event-fx id interceptors handler))

(defn reg-event-db [id handler]
  (rf/reg-event-db id interceptors handler))

(defn reg-chain-named [& handlers]
  (apply chain/reg-chain-named interceptors handlers))

(defn reg-chain [id & handlers]
  (apply chain/reg-chain id interceptors handlers))

(defn path-for [handler & params]
  (apply router/url handler params))