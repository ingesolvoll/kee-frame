(ns kee-frame.chain
  (:require [re-frame.core :as rf]))

(defn step-id [namespaced-keyword counter]
  (keyword
    (str (namespace namespaced-keyword) "/" (name namespaced-keyword) "-" counter)))

(defn make-step [id counter [type data]]
  (let [id (step-id id counter)]
    (case type
      :db `(do (rf/console :log "Adding chain step DB handler " ~id)
               (rf/reg-event-db ~id (fn [db# [event & args]] (assoc db# :dings :boms))))
      :fx `(do (rf/console :log "Adding chain step FX handler " ~id)
               (rf/reg-event-fx ~id (fn [])))
      :failure `(do (rf/console :log "Adding chain step failure handler " ~id)
                    (rf/reg-event-fx ~id (fn []))))))


(defmacro reg-event-chain [id & steps]
  (loop [step (first steps)
         next-steps (next steps)
         instructions []
         counter 0]
    (let [instructions (conj instructions (make-step id counter step))]
      (if-not next-steps
        `(do ~@instructions)
        (recur (first next-steps)
               (next next-steps)
               instructions
               (inc counter))))))