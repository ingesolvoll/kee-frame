(ns kee-frame.fsm.alpha.render
  (:require [re-frame.core :as f]
            [reagent.core :as r]))

(defmulti step
  "Materialized view of the current fsm state. A `step` method must
  exist for each state defined in the fsm transition map. States are
  globally defined, and namespaced keywords are required. It is a good
  idea to define the fsm in the same namespace as the steps."
  (fn [fsm & _]
    @(f/subscribe [::state fsm])))

(defmethod step :default
  [fsm & _]
  (r/with-let [step @(f/subscribe [::state fsm])]
              [:h2 (str "Undefined step: " step)]))

(defn- render*
  [fsm args]
  (r/with-let [_ (f/dispatch [::start fsm])]
              [apply step fsm args]
              (finally
               (f/dispatch [::stop fsm]))))

(defn render
  "Given an fsm function and arguments, renders a materialized view of
  the fsm state. A `step` method must exist for each state defined in
  the fsm transition map. The args passed to `render` must match the
  args expected by the fsm's `step` methods. There should be a one to
  one match bretween the fsm id and component identity, which
  guarantees that each unique fsm is started and stopped correctly."
  [fsm-fn & args]
  (let [fsm (apply fsm-fn args)]
    ^{:key (:id fsm)} [render* fsm args]))