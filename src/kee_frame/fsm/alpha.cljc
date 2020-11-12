(ns kee-frame.fsm.alpha
  (:require [statecharts.core :as fsm]
            [statecharts.integrations.re-frame :as fsm.rf]
            [re-frame.core :as f]
            #?(:cljs [reagent.core :as r])))


(f/reg-fx ::start
  (fn [{:keys [id] :as fsm}]
    (let [init-event       (keyword (name id) "init")
          transition-event (keyword (name id) "transition")]
      (-> fsm
          (assoc :integrations {:re-frame {:path             (f/path [:fsm id])
                                           :initialize-event init-event
                                           :transition-event transition-event}})
          fsm/machine
          fsm.rf/integrate)
      (f/dispatch [init-event]))))

(f/reg-event-fx ::start
  ;; Starts the interceptor for the given fsm.
  (fn [_ [_ fsm]]
    {::start fsm}))

(f/reg-sub ::state
  (fn [db [_ id]]
    (get-in db [:fsm id :_state])))

(f/reg-sub ::state-full
  (fn [db [_ id]]
    (get-in db [:fsm id])))

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