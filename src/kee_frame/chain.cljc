(ns kee-frame.chain
  (:require [re-frame.core :as rf]
            [clojure.walk :as walk]
            [kee-frame.spec :as spec]
            #?(:cljs [cljs.spec.alpha :as s]
               :clj  [clojure.spec.alpha :as s])
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
        next-event
        x))
    effects))

(defn single-valid-link [effects]
  (let [links (->> @state/links
                   (filter (fn [{:keys [get-dispatch effect-present?]}]
                             (and (effect-present? effects)
                                  (not (get-dispatch effects))))))]
    (when (= 1 (count links))
      (first links))))

(defn dispatch-empty-or-next [effects next-event-id]
  (when (or (not (:dispatch effects))
            (-> effects
                :dispatch
                first
                (= next-event-id)))
    {:get-dispatch :dispatch
     :set-dispatch (fn [effects event] (assoc effects :dispatch event))}))

(defn single-valid-next [next-event-id effects]
  (let [xs (->> @state/links
                (filter (fn [{:keys [get-dispatch]}]
                          (= next-event-id
                             (-> effects get-dispatch first)))))]
    (when (= 1 (count xs))
      (first xs))))

(defn select-link [next-event-id effects]
  (or
    (single-valid-next next-event-id effects)
    (single-valid-link effects)
    (dispatch-empty-or-next effects next-event-id)
    (throw
      (ex-info "Not possible to select next in chain"
               {:next-id  next-event-id
                :dispatch (:dispatch effects)
                :links    @state/links}))))

(defn make-event [next-event-id previous-event-params [_ & params]]
  (into [next-event-id] (concat previous-event-params params)))

(defn link-effects [next-event-id event-params effects]
  (if next-event-id
    (if-let [{:keys [set-dispatch get-dispatch]} (select-link next-event-id effects)]
      (set-dispatch effects (make-event next-event-id event-params (get-dispatch effects)))
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
  (let [chain-handlers (s/conform ::spec/chain-handlers step-fns)]
    (when (= ::s/invalid chain-handlers)
      (e/expound ::spec/chain-handlers step-fns)
      (throw (ex-info "Invalid chain. Should be functions or pairs of interceptor and function" (s/explain-data ::spec/chain-handlers step-fns))))
    (->> chain-handlers
         (partition 2 1 [nil])
         (map-indexed (fn [counter [current-handler next-handler]]
                        (let [{:keys [fn interceptors]} current-handler
                              id (step-id key counter)
                              next-id (when next-handler (step-id key (inc counter)))]
                          {:id            id
                           :next-id       next-id
                           :event-handler fn
                           :interceptors  interceptors
                           :interceptor   (chain-interceptor id next-id)}))))))

(defn register-chain-handlers! [instructions kee-frame-interceptors]
  (doseq [{:keys [id event-handler interceptor interceptors]} instructions]
    (when @state/debug?
      (rf/console :log "Registering chain handler fn " id))
    (rf/reg-event-fx id (into [interceptor] (concat kee-frame-interceptors interceptors)) event-handler)))

(defn reg-chain-named [interceptors & step-fns]
  (let [instructions (collect-named-event-instructions step-fns)]
    (register-chain-handlers! instructions interceptors)))

(defn reg-chain [id interceptors & step-fns]
  (let [instructions (collect-event-instructions id step-fns)]
    (register-chain-handlers! instructions interceptors)))