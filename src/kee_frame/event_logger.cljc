(ns ^:no-doc kee-frame.event-logger
  (:require [re-frame.interceptor :as i]
            [clojure.data :as data]
            [taoensso.timbre :as log]))

(def interceptor
  (i/->interceptor
   :id :event-logger
   :after (fn [context]
            (let [event   (i/get-coeffect context :event)
                  orig-db (i/get-coeffect context :db)
                  new-db  (i/get-effect context :db ::not-found)
                  effects (dissoc (i/get-effect context) :db)]

              (log/debug (merge {:event event}
                                (when (seq effects)
                                  {:side-effects effects})
                                (when (not= new-db ::not-found)
                                  (let [[only-before only-after] (data/diff orig-db new-db)
                                        db-changed? (or (some? only-before) (some? only-after))]
                                    (when db-changed?
                                      {:db-diff {:only-before only-before
                                                 :only-after  only-after}})))))
              context))))