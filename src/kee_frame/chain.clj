(ns kee-frame.chain
  (:require [re-frame.core :as rf]
            [clojure.walk :as walk]))

(defn step-id [event-id counter]
  (if (= 0 counter)
    event-id
    (keyword
      (str (namespace event-id) "/" (name event-id) "-" counter))))

(defn is? [x k]
  (and (vector? x)
       (= k (first x))))

(defn param? [x]
  (is? x :kee-frame.core/params))

(defn ctx? [x]
  (is? x :kee-frame.core/ctx))

(defn db? [x]
  (is? x :kee-frame.core/db))

(defn next? [x]
  (= x :kee-frame.core/next))

(defn walk-placeholders [ctx db params next-id data]
  (walk/postwalk
    (fn [x]
      (cond (param? x) `(nth ~params ~(second x))
            (ctx? x) (let [path (vec (next x))]
                       `(get-in ~ctx ~path))
            (db? x) (let [path (vec (next x))]
                      `(get-in ~db ~path))
            (and next-id (next? x)) next-id
            :pass-through x))
    data))

(defn pointer->assoc [pointer]
  (let [path (vec (butlast pointer))
        value (last pointer)]
    `(assoc-in ~path ~value)))

(defn update-db [db data]
  (update data :db #(->> %
                         (map pointer->assoc)
                         (concat `(-> ~db)))))

(defn rewrite-fx-handler [ctx db params {:keys [data next-id]}]
  (cond->> data
           true (walk-placeholders ctx db params next-id)
           (:db data) (update-db db)))

(defn rewrite-db-handler [ctx db params data]
  (->> data
       (walk-placeholders ctx db params nil)
       (map pointer->assoc)
       (concat `(-> ~db))))

(defn make-fx-event [step]
  (let [ctx (gensym "ctx")
        db (gensym "db")
        params (gensym "params")]
    `(fn [~ctx [_# & ~params]]
       (let [~db (:db ~ctx)]
         ~(rewrite-fx-handler ctx db params step)))))

(defn make-db-event [data]
  (let [ctx (gensym "ctx")
        db (gensym "db")
        params (gensym "params")]
    `(fn [~db [_# & ~params]] ~(rewrite-db-handler ctx db params data))))

(defn make-step [{:keys [id type data]
                  :as   step}]
  (case type
    :db `(do (rf/console :log "Adding chain step DB handler " ~id)
             (rf/reg-event-db ~id [rf/debug] ~(make-db-event data)))
    :fx `(do (rf/console :log "Adding chain step FX handler " ~id)
             (rf/reg-event-fx ~id [rf/debug] ~(make-fx-event step))) ;; TODO Add failure id as param
    :failure `(do (rf/console :log "Adding chain step failure handler " ~id)
                  (rf/reg-event-fx ~id (fn [])))))

(defmacro reg-chain [id & steps]
  (loop [step (first steps)
         next-steps (next steps)
         instructions []
         counter 0]
    (let [[type data] step
          next-id (when next-steps (step-id id (inc counter)))
          instruction (make-step {:id      (step-id id counter)
                                  :counter counter
                                  :type    type
                                  :data    data
                                  :next-id next-id})
          instructions (conj instructions instruction)]
      (if-not next-steps
        `(do ~@instructions)
        (recur (first next-steps)
               (next next-steps)
               instructions
               (inc counter))))))