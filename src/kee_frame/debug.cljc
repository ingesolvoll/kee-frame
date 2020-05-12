(ns ^:no-doc kee-frame.debug
  (:require [re-frame.interceptor :as i]
            [clojure.data :as data]
            [taoensso.timbre :as log]))

(def debug-interceptor
  (i/->interceptor
   :id :debug
   :before (fn debug-before
             [context]
             (let [event (i/get-coeffect context :event)]
               (log/debug "Processing event" (first event))
               context))
   :after (fn debug-after
            [context]
            (let [event   (i/get-coeffect context :event)
                  orig-db (i/get-coeffect context :db)
                  new-db  (i/get-effect context :db ::not-found)
                  effects (dissoc (i/get-effect context) :db)]

              (when (seq effects)
                (log/debug "Side effects caused" {:event   (first event)
                                                  :effects effects}))


              (when (not= new-db ::not-found)
                (let [[only-before only-after] (data/diff orig-db new-db)
                      db-changed? (or (some? only-before) (some? only-after))]
                  (when db-changed?
                    (log/debug "Client db diff"
                               {:event       (first event)
                                :only-before only-before
                                :only-after  only-after}))))
              context))))