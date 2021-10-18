(ns kee-frame.fsm.beta
  (:require [statecharts.core :as fsm]
            [statecharts.integrations.re-frame :as fsm.rf]
            [re-frame.core :as f]
            #?(:cljs [reagent.core :as r])))


(f/reg-fx ::start
  (fn [{:keys [id transition-event] :as fsm}]
    (let [init-event       (keyword (name id) "init")
          transition-event (or transition-event
                               (keyword (name id) "transition"))]
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

(defn match? [state value]
  (when (seq state)
    (or (= state value)
        (match? (butlast state) value))))

(defn match-state [state & pairs]
  (loop [[first-pair & rest-pairs] (partition-all 2 pairs)]
    (cond

      (some-> first-pair seq count (= 2))
      (let [[value component] first-pair]
        (if (match? state value)
          component
          (recur rest-pairs)))

      (some-> first-pair seq count (= 1))
      (first first-pair)

      :else
      (throw (ex-info "Could not find a component to match state."
                      {:state state
                       :pairs pairs})))))
