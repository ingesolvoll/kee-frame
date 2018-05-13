(ns kee-frame.core
  (:require [kee-frame.state :as state]
            [kee-frame.router :as router]
            [kee-frame.chain :as chain]
            [kee-frame.websocket :as websocket]
            [re-frame.core :as rf :refer [console]]
            [kee-frame.spec :as spec :refer [spec-interceptor]]
            [kee-frame.debug :refer [debug-interceptor]]
            [clojure.spec.alpha :as s]
            [expound.alpha :as e]
            [kee-frame.interop :as interop]))

(def kee-frame-interceptors [(spec-interceptor state/app-db-spec) (debug-interceptor state/debug?) rf/trim-v])

(def valid-option-key? #{:router :routes :process-route :debug? :chain-links :app-db-spec :root-component :initial-db})

(defn extra-options [options]
  (->> options
       (filter (fn [[k]] (not (valid-option-key? k))))
       (into {})))

(defn start! [options]
  (when-not (s/valid? ::spec/start-options options)
    (e/expound ::spec/start-options options)
    (throw (ex-info "Invalid options" (s/explain-data ::spec/start-options options))))
  (let [extras (extra-options options)]
    (when (seq extras)
      (throw (ex-info (str "Uknown startup options. Valid keys are " valid-option-key?) extras))))
  (router/start! options))

(defn reg-controller [id controller]
  (when-not (s/valid? ::spec/controller controller)
    (e/expound ::spec/controller controller)
    (throw (ex-info "Invalid controller" (s/explain-data ::spec/controller controller))))
  (when (get @state/controllers id)
    (console :warn "Overwriting controller with id " id))
  (swap! state/controllers update id merge controller))

(defn reg-event-fx
  ([id handler] (reg-event-fx id nil handler))
  ([id interceptors handler] (rf/reg-event-fx id (concat kee-frame-interceptors interceptors) handler)))

(defn reg-event-db
  ([id handler] (reg-event-db id nil handler))
  ([id interceptors handler] (rf/reg-event-db id (concat kee-frame-interceptors interceptors) handler)))

(defn reg-chain-named [& handlers]
  (apply chain/reg-chain-named kee-frame-interceptors handlers))

(defn reg-chain [id & handlers]
  (apply chain/reg-chain id kee-frame-interceptors handlers))

(defn path-for [handler & params]
  (apply router/url handler params))

(defn switch-route [f & pairs]
  (apply router/switch-route f pairs))

(rf/reg-fx ::websocket-open (partial websocket/start-websocket interop/create-socket))

(rf/reg-fx ::websocket-close websocket/close-socket)

(rf/reg-fx ::websocket-send websocket/ws-send!)