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
      (cond (param? x) (let [[_ index updater] x]
                         (if updater
                           `(~updater (nth ~params ~index))
                           `(nth ~params ~index)))
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

(defn update-http [next-id data]
  (if-not (get-in data [:http-xhrio :on-success])
    (if next-id
      (update data :http-xhrio assoc :on-success [:kee-frame.core/next])
      (throw (ex-info "HTTP success needs a next step in chain" {:got :nothing})))
    data))

(defn insert-dispatch [next-id {:keys [http-xhrio dispatch] :as data}]
  (let [skip? (or http-xhrio dispatch)]
    (cond
      skip? data
      next-id (assoc data :dispatch [next-id])
      :else data)))

(defn rewrite-fx-handler [ctx db params {:keys [data next-id]}]
  (cond->> data

           (:http-xhrio data)
           (update-http next-id)

           true
           (walk-placeholders ctx db params next-id)

           (:db data)
           (update-db db)

           true
           (insert-dispatch next-id)))

(defn make-fx-event [step]
  (let [ctx (gensym "ctx")
        db (gensym "db")
        params (gensym "params")]
    `(fn [~ctx [_# & ~params]]
       (let [~db (:db ~ctx)]
         ~(rewrite-fx-handler ctx db params step)))))

(defn make-step [{:keys [id]
                  :as   step}]
  `(do (rf/console :log "Adding chain step FX handler " ~id)
       (rf/reg-event-fx ~id [rf/debug] ~(make-fx-event step))))

(defmacro reg-chain [id & steps]
  (loop [data (first steps)
         next-steps (next steps)
         instructions []
         counter 0]
    (let [next-id (when next-steps (step-id id (inc counter)))
          instruction (make-step {:id      (step-id id counter)
                                  :counter counter
                                  :data    data
                                  :next-id next-id})
          instructions (conj instructions instruction)]
      (if-not next-steps
        `(do ~@instructions)
        (recur (first next-steps)
               (next next-steps)
               instructions
               (inc counter))))))