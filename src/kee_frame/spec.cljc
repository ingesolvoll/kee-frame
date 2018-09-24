(ns ^:no-doc kee-frame.spec
  (:require [re-frame.interceptor :refer [->interceptor get-effect get-coeffect assoc-coeffect assoc-effect]]
            [re-frame.core :refer [console]]
            [clojure.spec.alpha :as s]
            [re-chain.core :as chain]
            [expound.alpha :as e]
            [kee-frame.api :as api]))

(s/def ::params (s/or :path-vector vector? :fn fn?))
(s/def ::start (s/or :vector ::event-vector :fn fn?))
(s/def ::stop (s/or :vector ::event-vector :fn fn?))

(s/def ::controller (s/keys :req-un [::params ::start]
                            :opt-un [::stop]))

(s/def ::event-vector (s/cat :event-key keyword? :event-args (s/* any?)))

(s/def ::routes any?)
(s/def ::router #(satisfies? api/Router %))
(s/def ::hash-routing? (s/nilable boolean?))
(s/def ::root-component (s/nilable vector?))
(s/def ::initial-db (s/nilable map?))
(s/def ::app-db-spec (s/nilable keyword?))
(s/def ::blacklist (s/coll-of keyword? :kind set?))
(s/def ::debug? (s/nilable (s/or :boolean boolean?
                                 :config (s/keys :opt-un [::blacklist]))))
(s/def ::chain-links ::chain/links)
(s/def ::breakpoints vector?)
(s/def ::debounce-ms number?)
(s/def ::screen (s/nilable (s/or :boolean boolean?
                                 :config (s/keys :req-un [::breakpoints ::debounce-ms]))))

(s/def ::start-options (s/keys :opt-un [::routes ::router ::hash-routing? ::root-component ::initial-db ::app-db-spec ::debug? ::chain-links ::screen]))

(s/def ::route-data (s/cat :route-name keyword? :path-params (s/* (s/map-of keyword? any?))))

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