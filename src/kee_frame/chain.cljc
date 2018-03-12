(ns kee-frame.chain
  (:require [re-frame.core :as rf]
            [clojure.walk :as walk]
    #?(:cljs
       [cljs.spec.alpha :as s])
    #?(:clj
            [clojure.spec.alpha :as s])
            [kee-frame.state :as state]))

(defn step-id [event-id counter]
  (if (= 0 counter)
    event-id
    (keyword
      (str (namespace event-id) (if (namespace event-id) "/") (name event-id) "-" counter))))

(defn replace-pointers [next-event effects]
  (walk/postwalk
    (fn [x]
      (if (= x :kee-frame.core/next)
        next-event                                          ;; (throw (ex-info "Found next pointer, but no next step" {:token x}))
        x))
    effects))

(def links {:http-xhrio :on-success})

(defn single-valid-link [potential specified]
  (when (and (= 1 (count potential))
             (->> specified
                  (filter (fn [[path]] (= path (first potential))))
                  count
                  (= 0)))
    (first potential)))

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

(defn single-valid-next [next-event-id specified-links]
  (let [xs (->> specified-links
                (filter (fn [[_ value]]
                          (= next-event-id (first value)))))]
    (when (-> xs count (= 1))
      (ffirst xs))))

(defn dispatch-empty-or-next [effects next-event-id]
  (when (or (not (:dispatch effects))
            (-> effects
                :dispatch
                first
                (= next-event-id)))
    [:dispatch]))

(defn select-link [next-event-id links effects]
  (let [potential (potential-links links effects)
        specified (specified-links links effects)]
    (or
      (single-valid-next next-event-id specified)
      (single-valid-link potential specified)
      (dispatch-empty-or-next effects next-event-id)
      (throw
        (ex-info "Not possible to select next in chain"
                 {:next-id         next-event-id
                  :dispatch        (:dispatch effects)
                  :potential-links potential
                  :specified-links specified})))))

(defn make-event [next-event-id previous-event-params specified-event]
  (into [next-event-id] (concat previous-event-params (rest specified-event))))

(defn link-effects [next-event-id event-params links effects]
  (if next-event-id
    (if-let [selected-link (select-link next-event-id links effects)]
      (assoc-in effects selected-link (make-event next-event-id event-params (get-in effects selected-link)))
      effects)
    effects))

(defn effect-postprocessor [next-event-id]
  (fn [ctx]
    (let [event-params (rest (rf/get-coeffect ctx :event))]
      (update ctx :effects #(->> %
                                 (replace-pointers next-event-id)
                                 (link-effects next-event-id event-params links))))))

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

(defn reg-chain [id interceptors & step-fns]
  (let [instructions (collect-event-instructions id step-fns)]
    (doseq [{:keys [id event-handler interceptor]} instructions]
      (when @state/debug?
        (rf/console :log "Registering chain handler fn " id))
      (rf/reg-event-fx id (into [interceptor] interceptors) event-handler))))