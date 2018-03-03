(ns kee-frame.chain
  (:require [re-frame.core :as rf]
            [clojure.walk :as walk]))

(defn step-id [namespaced-keyword counter]
  (keyword
    (str (namespace namespaced-keyword) "/" (name namespaced-keyword) "-" counter)))

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

(defn rewrite-fx-handler [ctx db params next-id data]
  (->> data
       (walk-placeholders ctx db  params next-id)))


(defn pointer->assoc [pointer]
  (let [path (vec (butlast pointer))
        value (last pointer)]
    `(assoc-in ~path ~value)))

(defn rewrite-db-handler [ctx db params next-id data]
  (->> data
       (walk-placeholders ctx db params next-id)
       (map pointer->assoc)
       (concat `(-> ~db))))

(defn make-fx-event [data next-id]
  (let [ctx (gensym "ctx")
        db (gensym "db")
        params (gensym "params")]
    `(fn [{:keys [~db] :as ~ctx} [_# & ~params]] ~(rewrite-fx-handler ctx db params next-id data))))

(defn make-db-event [data]
  (let [ctx (gensym "ctx")
        db (gensym "db")
        params (gensym "params")]
    `(fn [~db [_# & ~params]] ~(rewrite-db-handler ctx db params nil data))))

(defn make-step [id counter [type data]]
  (let [event-id (if (= 0 counter)
                   id
                   (step-id id counter))
        next-id (step-id id (inc counter))]
    (case type
      :db `(do (rf/console :log "Adding chain step DB handler " ~event-id)
               (rf/reg-event-db ~event-id [rf/debug] ~(make-db-event data)))
      :fx `(do (rf/console :log "Adding chain step FX handler " ~event-id)
               (rf/reg-event-fx ~event-id [rf/debug] ~(make-fx-event data next-id))) ;; TODO Add failure id as param
      :failure `(do (rf/console :log "Adding chain step failure handler " ~event-id)
                    (rf/reg-event-fx ~event-id (fn []))))))


(defmacro reg-chain [id & steps]
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