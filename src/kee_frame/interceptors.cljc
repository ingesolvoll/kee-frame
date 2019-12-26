(ns kee-frame.interceptors
  (:require [re-frame.core :as f]
            [re-frame.registrar :as reg]))

(defn reg-global-interceptor
  [id interceptor]
  (reg/register-handler :global-interceptor id interceptor))

(defn clear-global-interceptor
  [id]
  (swap! reg/kind->id->handler
         update-in
         [:global-interceptor]
         dissoc id))

(def add-global-interceptors
  "Adds global interceptors to the beginning of queue."
  (letfn [(cut-in-queue [queue xs]
            (let [q (empty queue)]
              (into q (concat xs queue))))
          (add-global-interceptors* [context]
            (let [globals (vals (reg/get-handler :global-interceptor))]
              (update context :queue cut-in-queue globals)))]
    (f/->interceptor
     :id :add-global-interceptors
     :before add-global-interceptors*)))

