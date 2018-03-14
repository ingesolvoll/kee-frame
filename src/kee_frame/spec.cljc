(ns kee-frame.spec
  (:require [re-frame.interceptor :refer [->interceptor get-effect get-coeffect assoc-coeffect assoc-effect]]
            [re-frame.core :refer [console]]
            [clojure.spec.alpha :as s]))

(s/def ::params (s/or :path-vector vector? :fn ifn?))
(s/def ::start (s/or :vector ::event-vector :fn ifn?))
(s/def ::stop (s/or :vector ::event-vector :fn ifn?))

(s/def ::controller (s/keys :req-un [::params ::start]
                            :opt-un [::stop]))

(s/def ::chain-handler ifn?)
(s/def ::chain-handlers (s/* ::chain-handler))
(s/def ::named-chain-handlers (s/* (s/cat :id keyword? :event-handler ::chain-handler)))

(s/def ::event-vector (s/cat :event-key keyword? :event-args (s/* any?)))

(defn log-spec-error [new-db spec]
  (console :group "*** Spec error when updating DB, rolling back ***")
  (let [{:keys [::s/problems ::s/spec]} (s/explain-data spec new-db)]
    (console :log "Problems " problems)
    (console :log "Failing spec " spec))
  (console :groupEnd "*****************************"))

(defn rollback [context new-db db-spec-atom]
  (do
    (log-spec-error new-db @db-spec-atom)
    (assoc-effect context :db (get-coeffect context :db))))

(defn spec-interceptor [db-spec-atom]
  (->interceptor
    :id :spec
    :after (fn [context]
             (let [new-db (get-effect context :db)]
               (if (and @db-spec-atom new-db (not (s/valid? @db-spec-atom new-db)))
                 (rollback context new-db db-spec-atom)
                 context)))))