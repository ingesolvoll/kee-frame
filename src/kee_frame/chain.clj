(ns kee-frame.chain
  (:require [re-frame.core :as rf]
            [clojure.walk :as walk]))

(defn step-id [namespaced-keyword counter]
  (keyword
    (str (namespace namespaced-keyword) "/" (name namespaced-keyword) "-" counter)))

(defn rewrite-db-handler [data db params]
  (->> data
       (walk/postwalk
         (fn [x]
           (if (and (vector? x)
                    (= :kee-frame.core/params (first x)))
             `(nth ~params ~(second x))
             x)))
       (map (fn [pointer]
              (let [path (vec (butlast pointer))
                    value (last pointer)]
                `(assoc-in ~path ~value))))
       (concat `(-> ~db))))

(defn make-db-event [data]
  (let [db (gensym "db")
        params (gensym "params")]
    `(fn [~db [_# & ~params]] ~(rewrite-db-handler data db params))))

(defn make-step [id counter [type data]]
  (let [id (step-id id counter)]
    (case type
      :db `(do (rf/console :log "Adding chain step DB handler " ~id)
               (rf/reg-event-db ~id [rf/debug] ~(make-db-event data)))
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