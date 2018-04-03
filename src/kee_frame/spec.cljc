(ns kee-frame.spec
  (:require [re-frame.interceptor :refer [->interceptor get-effect get-coeffect assoc-coeffect assoc-effect]]
            [re-frame.core :refer [console]]
            [clojure.spec.alpha :as s]
            [expound.alpha :as e]))

(s/def ::params (s/or :path-vector vector? :fn ifn?))
(s/def ::start (s/or :vector ::event-vector :fn ifn?))
(s/def ::stop (s/or :vector ::event-vector :fn ifn?))

(s/def ::controller (s/keys :req-un [::params ::start]
                            :opt-un [::stop]))

(s/def ::chain-handler ifn?)
(s/def ::chain-handlers (s/* ::chain-handler))
(s/def ::named-chain-handlers (s/* (s/cat :id keyword? :event-handler ::chain-handler)))

(s/def ::event-vector (s/cat :event-key keyword? :event-args (s/* any?)))

(s/def ::routes any?)
(s/def ::root-component (s/nilable vector?))
(s/def ::initial-db (s/nilable map?))
(s/def ::match-route (s/nilable fn?))
(s/def ::app-db-spec (s/nilable keyword?))
(s/def ::blacklist (s/coll-of keyword? :kind set?))
(s/def ::debug? (s/nilable (s/or :boolean boolean?
                                 :config (s/keys :opt-un [::blacklist]))))

(s/def ::start-options (s/keys :opt-un [::routes ::root-component ::initial-db ::match-route ::app-db-spec ::debug?]))

(defn log-spec-error [new-db spec]
  (console :group "*** Spec error when updating DB, rolling back ***")
  (e/expound spec new-db)
  (console :groupEnd "*****************************"))

(defn rollback [context new-db db-spec]
  (do
    (log-spec-error new-db db-spec)
    (assoc-effect context :db (get-coeffect context :db))))

(defn spec-interceptor [db-spec-atom]
  (->interceptor
    :id :spec
    :after (fn [context]
             (let [new-db (get-effect context :db)]
               (if (and @db-spec-atom new-db (not (s/valid? @db-spec-atom new-db)))
                 (rollback context new-db @db-spec-atom)
                 context)))))