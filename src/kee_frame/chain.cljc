(ns kee-frame.chain
  (:require [re-frame.core :as rf]
            [clojure.walk :as walk]
            [kee-frame.spec :as spec]
    #?(:cljs
       [cljs.spec.alpha :as s])
    #?(:clj
            [clojure.spec.alpha :as s])
            [kee-frame.state :as state]
            [expound.alpha :as e]))

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

(defn single-valid-link [potential specified]
  (when (and (= 1 (count potential))
             (->> specified
                  (filter (fn [[path]] (= path (-> potential first :path))))
                  count
                  (= 0)))
    (-> potential first :path)))

(defn specified-links [effects]
  (->> @state/links
       (map (fn [{:keys [path]}]
              [path (get-in effects path)]))
       (filter (comp identity second))))

(defn potential-links [effects]
  (filter (fn [{:keys [path]}]
            ((first path) effects))
          @state/links))

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

(defn select-link [next-event-id effects]
  (let [potential (potential-links effects)
        specified (specified-links effects)]
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

(defn link-effects [next-event-id event-params effects]
  (if next-event-id
    (if-let [selected-link (select-link next-event-id effects)]
      (assoc-in effects selected-link (make-event next-event-id event-params (get-in effects selected-link)))
      effects)
    effects))

(defn effect-postprocessor [next-event-id]
  (fn [ctx]
    (let [event-params (rest (rf/get-coeffect ctx :event))]
      (update ctx :effects #(->> %
                                 (replace-pointers next-event-id)
                                 (link-effects next-event-id event-params))))))

(defn chain-interceptor [current-event-id next-event-id]
  (rf/->interceptor
    :id current-event-id
    :after (effect-postprocessor next-event-id)))

(defn collect-named-event-instructions [step-fns]
  (let [chain-handlers (s/conform ::spec/named-chain-handlers step-fns)]
    (when (= ::s/invalid chain-handlers)
      (e/expound ::spec/named-chain-handlers step-fns)
      (throw (ex-info "Invalid named chain. Should be pairs of keyword and handler" (s/explain-data ::spec/named-chain-handlers step-fns))))
    (->> chain-handlers
         (partition 2 1 [nil])
         (map (fn [[{:keys [id] :as handler-1} handler-2]]
                (let [next-id (:id handler-2)]
                  (assoc handler-1 :next-id (:id handler-2)
                                   :interceptor (chain-interceptor id next-id))))))))

(defn collect-event-instructions [key step-fns]
  (when-not (s/valid? ::spec/chain-handlers step-fns)
    (e/expound ::spec/chain-handlers step-fns)
    (throw (ex-info "Invalid chain" (s/explain-data ::spec/chain-handlers step-fns))))

  (->> step-fns
       (partition 2 1 [nil])
       (map-indexed (fn [counter [current-handler next-handler]]
                      (let [id (step-id key counter)
                            next-id (when next-handler (step-id key (inc counter)))]
                        {:id            id
                         :next-id       next-id
                         :event-handler current-handler
                         :interceptor   (chain-interceptor id next-id)})))))

(defn register-chain-handlers! [instructions interceptors]
  (doseq [{:keys [id event-handler interceptor]} instructions]
    (when @state/debug?
      (rf/console :log "Registering chain handler fn " id))
    (rf/reg-event-fx id (into [interceptor] interceptors) event-handler)))

(defn reg-chain-named [interceptors & step-fns]
  (let [instructions (collect-named-event-instructions step-fns)]
    (register-chain-handlers! instructions interceptors)))

(defn reg-chain [id interceptors & step-fns]
  (let [instructions (collect-event-instructions id step-fns)]
    (register-chain-handlers! instructions interceptors)))