(ns kee-frame.chain
  (:require [re-frame.core :as rf]
            [clojure.walk :as walk]
    #?(:cljs
       [cljs.spec.alpha :as s])
    #?(:clj
            [clojure.spec.alpha :as s])))

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

(defn update-http [next-id params data]
  (if-not (get-in data [:http-xhrio :on-success])
    (if next-id
      (update data :http-xhrio assoc :on-success `(into [~next-id] ~params))
      (throw (ex-info "HTTP success needs a next step in chain" data)))
    data))

(defn insert-dispatch [next-id params {:keys [http-xhrio dispatch] :as data}]
  (let [skip? (or http-xhrio dispatch)]
    (cond
      skip? data
      next-id (assoc data :dispatch `(into [~next-id] ~params))
      :else data)))

(defn rewrite-fx-handler [ctx db params {:keys [data next-id]}]
  (cond->> data

           (:http-xhrio data)
           (update-http next-id params)

           true
           (walk-placeholders ctx db params next-id)

           (:db data)
           (update-db db)

           true
           (insert-dispatch next-id params)))

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

(s/def ::step map?)
(s/fdef reg-chain
        :args (s/cat :id keyword?
                     :steps (s/* ::step)))

(defn replace-pointers [next-event effects]
  (walk/postwalk
    (fn [x]
      (if (next? x)
        next-event                                          ;; (throw (ex-info "Found next pointer, but no next step" {:token x}))
        x))
    effects))

(def links {:http-xhrio :on-success})

(defn one-valid-link? [potential specified]
  (and (= 1 (count potential))
       (->> specified
            (filter (fn [[path]] (= path (first potential))))
            count
            (= 0))))

(defn cleanup-link [link] (filter identity link))

(defn specified-links [links effects]
  (->> links
       (map (fn [link]
              [(cleanup-link link) (get-in effects (cleanup-link link))]))
       (filter (comp identity second))))

(defn potential-links [links effects]
  (->> links
       (filter (fn [[path]]
                 (or (nil? path) (path effects))))
       (map (fn [link]
              (cleanup-link link)))))

(defn next-already-valid? [next-event-id specified-links]
  (->> specified-links
       (filter (fn [[_ value]]
                 (#{:kee-frame.core/next next-event-id} (first value))))
       count
       (= 1)))

(defn select-link [next-event-id links effects]
  (let [potential (potential-links links effects)
        specified (specified-links links effects)]
    (cond
      (next-already-valid? next-event-id specified) nil
      (one-valid-link? potential specified) (first potential)
      (not (:dispatch effects)) [:dispatch]
      :else (throw
              (ex-info "Not possible to select next in chain"
                       {:next-id         next-event-id
                        :dispatch        (:dispatch effects)
                        :potential-links potential
                        :specified-links specified})))))


(defn link-effects [next-event-id links effects]
  (if next-event-id
    (if-let [selected-link (select-link next-event-id links effects)]
      (assoc-in effects selected-link [next-event-id])
      effects)
    effects))

(defn effect-postprocessor [next-event-id]
  (fn [ctx]
    (->> ctx
         :effects
         (link-effects next-event-id links)
         (replace-pointers next-event-id)
         (assoc ctx :effects))))

(defn chain-interceptor [current-event-id next-event-id]
  (rf/->interceptor
    :id current-event-id
    :after (effect-postprocessor next-event-id)))

(defn collect-event-instructions [id step-fns]
  (loop [[step-fn & next-step-fns] step-fns
         instruction-maps []
         counter 0]
    (let [current-id (step-id id counter)
          next-id (when (seq next-step-fns) (step-id id (inc counter)))
          instructions (conj instruction-maps {:id            current-id
                                               :event-handler step-fn
                                               :interceptor   (chain-interceptor current-id next-id)})]
      (if-not (seq next-step-fns)
        instructions
        (recur next-step-fns
               instructions
               (inc counter))))))

(defn reg-chain-2 [id & step-fns]
  (let [instructions (collect-event-instructions id step-fns)]
    (doseq [{:keys [id event-handler interceptor]} instructions]
      (rf/console :log "Registering chain handler fn " id)
      (rf/reg-event-fx id [interceptor rf/debug] event-handler))))