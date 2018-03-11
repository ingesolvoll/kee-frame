(ns kee-frame.spec
  (:require [re-frame.interceptor :refer [->interceptor get-effect get-coeffect assoc-coeffect assoc-effect]]
            [re-frame.core :refer [console]]
            [kee-frame.state :as state]
            [clojure.spec.alpha :as s]))

(defn log-spec-error [new-db spec]
  (console :group "*** Spec error when updating DB, rolling back ***")
  (let [{:keys [::s/problems ::s/spec]} (s/explain-data spec new-db)]
    (console :log "Problems " problems)
    (console :log "Failing spec " spec))
  (console :groupEnd "*****************************"))

(defn spec-interceptor [db-spec-atom]
  (->interceptor
    :id :spec
    :after (fn [context]
             (let [new-db (get-effect context :db)]
               (if (and @db-spec-atom new-db (not (s/valid? @db-spec-atom new-db)))
                 (do
                   (log-spec-error new-db @db-spec-atom)
                   (assoc-effect context :db (get-coeffect context :db)))
                 context)))))