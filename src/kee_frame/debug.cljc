(ns ^:no-doc kee-frame.debug
  (:require [re-frame.core :refer [console]]
            [re-frame.interceptor :refer [->interceptor get-effect get-coeffect assoc-coeffect assoc-effect]]
            [clojure.data :as data]
            [kee-frame.state :as state]))

(defn debug-enabled? [[event-key]]
  (let [{:keys [blacklist events?]
         :or   {events? true}} @state/debug-config]
    (and @state/debug?
         events?
         (not (and blacklist
                   (blacklist event-key))))))

(defn debug-interceptor [debug?]
  (->interceptor
    :id :debug
    :before (fn debug-before
              [context]
              (let [event (get-coeffect context :event)]
                (when (debug-enabled? event)
                  (console :log "Handling event " event))
                context))
    :after (fn debug-after
             [context]
             (let [event (get-coeffect context :event)
                   orig-db (get-coeffect context :db)
                   new-db (get-effect context :db ::not-found)
                   effects (dissoc (get-effect context) :db)]

               (when (and (debug-enabled? event) (seq effects))
                 (console :log "Side effects caused by event " (first event) ": " effects))

               (when (and (debug-enabled? event) (not= new-db ::not-found))
                 (let [[only-before only-after] (data/diff orig-db new-db)
                       db-changed? (or (some? only-before) (some? only-after))]
                   (when db-changed?
                     (console :group "db clojure.data/diff for:" (first event))
                     (console :log "only before:" only-before)
                     (console :log "only after :" only-after)
                     (console :groupEnd))))
               context))))