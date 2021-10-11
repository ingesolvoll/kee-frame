(ns kee-frame.fsm.alpha
  (:require [clojure.spec.alpha :as s]
            [re-frame.core :as f]
            [kee-frame.interop :as interop]
            [taoensso.timbre :as log]
            #?(:cljs [reagent.core :as r])))

(defn reg-no-op
  "Convenience function for declaring no-op events."
  [id]
  (f/reg-event-fx id (constantly nil)))

(defn find-transition
  "Try to find a transition that matches some subset of the received event"
  [transitions event]
  (when (seq event)
    (get transitions event
         (find-transition transitions (butlast event)))))

(defn- event-transition!
  "Given a transition map and an event, returns the next fsm state if
  there is a valid transition, `nil` otherwise. Event transition
  `:when` clause is optionally applied."
  [transitions event]
  (if-let [transition (find-transition transitions event)]
    (let [{next-state :to
           dispatch   :dispatch} transition]
      (when dispatch
        (doseq [e dispatch]
          (f/dispatch e)))
      next-state)
    (log/trace {:type        :fsm-transition-missed
                :transitions transitions
                :event       event})))

(defn foreign-event? [fsm-id [event-id _ event-fsm-id]]
  (and (#{::on-enter ::timeout} event-id)
       (not= fsm-id event-fsm-id)))

(defn next-state
  "Returns next state if there is a valid transition, `nil` otherwise."
  [fsm db event]
  (let [{id  :id state-attr :state-attr start :start transition-map :fsm
         :or {state-attr ::state}} fsm
        current-state (get-in db [id state-attr] start)
        transitions   (get transition-map current-state)]
    (when-not (foreign-event? id event)
      (event-transition! transitions event))))

;;;;;;;;;;;; Timeout implementation ;;;;;;;;;;;;;;;;;;;

(reg-no-op ::timeout)
(reg-no-op ::on-enter)
(reg-no-op ::fsm-started)


(reg-no-op :default-on-failure)

(defn compile-timeouts [fsm]
  (->> fsm
       :fsm
       (map (fn [[state transitions]]
              [state (->> transitions
                          (filter (fn [[[event-id]] _]
                                    (#{::timeout ::on-enter} event-id)))
                          (mapv (fn [[[event-id timeout]] _]
                                  (let [timeout (or timeout 0)]
                                    {:ms timeout :dispatch [event-id timeout (:id fsm)]}))))]))
       (into {})))

(defn clear-timeouts! [timeouts*]
  (doseq [t @timeouts*]
    (interop/clear-timeout t))
  (reset! timeouts* []))

(defn dispatch-timeouts! [timeouts* timeouts]
  (->> timeouts
       (mapv (fn [{:keys [ms dispatch]}]
               (interop/set-timeout #(f/dispatch dispatch) ms)))
       (reset! timeouts*)))

(defn state-changed? [prev next]
  (or
   (not prev)
   next))

(defn advance
  "Given a parsed fsm, a db, and an event, advances the fsm. Else,
  no-op."
  [fsm timeouts* state->timeouts db event]
  (let [{:keys [id state-attr stop start] :or {state-attr ::state}} fsm
        current-state (get-in db [id state-attr])
        next-state    (next-state fsm db event)]
    (when (state-changed? current-state next-state)
      (log/debug {:type           :fsm-transition
                  :event          event

                  :leaving-state  current-state
                  :entering-state next-state})
      (clear-timeouts! timeouts*)
      (dispatch-timeouts! timeouts* (state->timeouts (or next-state start))))
    (when (and stop (= next-state stop))
      (f/dispatch [::stop fsm]))
    (assoc-in db [id state-attr] (or next-state current-state start))))

(f/reg-fx ::start
  (fn [{:keys [id] :as fsm}]
    (let [timeouts*       (atom nil)
          state->timeouts (compile-timeouts fsm)]
      (-> (partial advance fsm timeouts* state->timeouts)
          f/enrich
          (assoc :id id)
          (f/reg-global-interceptor)))))

(f/reg-fx ::stop
  (fn [{:keys [id]}]
    (when id
      (f/clear-global-interceptor id))))

(f/reg-event-fx ::start
                ;; Starts the interceptor for the given fsm.
  (fn [_ [_ fsm]]
    {::start   fsm
     :dispatch [::fsm-started]}))

(f/reg-event-fx ::stop
  ;; Stops the interceptor for the given fsm.
  (fn [_ [_ fsm]]
    {::stop fsm}))

(f/reg-sub ::state
  (fn [db [_ {:keys [id state-attr start]
              :or   {state-attr ::state}}]]
    (get-in db [id state-attr] start)))

(s/def ::binding (s/and vector?
                        (s/cat :fsm-symbol symbol? :fsm any?)))

(defmacro with-fsm [binding & body]
  (let [parsed (s/conform ::binding binding)]
    (when (= ::s/invalid parsed)
      (throw (ex-info "with-fsm accepts exactly one binding pair, the symbol and the value containing the fsm."
                      (s/explain-data ::binding binding))))
    (let [{:keys [:fsm-symbol :fsm]} parsed]
      `(reagent.core/with-let [~fsm-symbol ~fsm
                               _# (f/dispatch [::start ~fsm-symbol])]
         ~@body
         ~(list
           'finally
           `(re-frame.core/dispatch [::stop ~fsm-symbol]))))))

(defmulti step
  "Materialized view of the current fsm state. A `step` method must
  exist for each state defined in the fsm transition map. States are
  globally defined, and namespaced keywords are required. It is a good
  idea to define the fsm in the same namespace as the steps."
  (fn [fsm & _]
    @(f/subscribe [::state fsm])))

(defmethod step :default
  [fsm & _]
  [:h2 (str "Undefined step: " @(f/subscribe [::state fsm]))])

#?(:cljs
   (defn- render*
     [fsm args]
     (r/with-let [_ (f/dispatch [::start fsm])]
       [apply step fsm args]
       (finally
        (f/dispatch [::stop fsm])))))

#?(:cljs
   (defn render
     "Given an fsm function and arguments, renders a materialized view of
     the fsm state. A `step` method must exist for each state defined in
     the fsm transition map. The args passed to `render` must match the
     args expected by the fsm's `step` methods."
     [fsm-fn & args]
     (let [fsm (apply fsm-fn args)]
       ^{:key (:id fsm)} [render* fsm args])))