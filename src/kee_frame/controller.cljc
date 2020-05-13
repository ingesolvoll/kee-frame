(ns ^:no-doc kee-frame.controller
  (:require
   #?(:cljs
      [cljs.core.match :refer [match]])
   #?(:clj
      [clojure.core.match :refer [match]])
   [kee-frame.state :as state]
   [kee-frame.spec :as spec]
   [kee-frame.fsm.alpha :as fsm]
   [clojure.spec.alpha :as s]
   [expound.alpha :as e]
   [taoensso.timbre :as log]
   [re-frame.core :as rf]))

(defn process-params [params route]
  (cond
    (vector? params) (get-in route params)
    (ifn? params) (params route)))

(defn validate-and-dispatch! [dispatch]
  (when dispatch
    (if (map? dispatch)
      [::fsm/start dispatch]
      (do
        (when-not (s/valid? ::spec/event-vector dispatch)
          (e/expound ::spec/event-vector dispatch)
          (throw (ex-info "Invalid dispatch value"
                          (s/explain-data ::spec/event-vector dispatch))))
        dispatch))))

(defn stop-controller [ctx {:keys [stop]}]
  (cond
    (vector? stop) stop
    (ifn? stop) (validate-and-dispatch! (stop ctx))))

(defn start-controller [ctx {:keys [last-params start]}]
  (when start
    (cond
      (vector? start) start
      (ifn? start) (validate-and-dispatch! (start ctx last-params)))))

(defn controller-actions [controllers route]
  (reduce (fn [actions [id {:keys [last-params params start stop]}]]
            (let [current-params (process-params params route)
                  controller     {:id          id
                                  :start       start
                                  :stop        stop
                                  :last-params current-params}]
              (match [last-params current-params (= last-params current-params)]
                     [_ _ true] actions
                     [nil _ false] (update actions :start conj controller)
                     [_ nil false] (update actions :stop conj controller)
                     [_ _ false] (-> actions
                                     (update :stop conj controller)
                                     (update :start conj controller)))))
          {}
          controllers))

(defn update-controllers [controllers new-controllers]
  (reduce (fn [result {:keys [id last-params]}]
            (assoc-in result [id :last-params] last-params))
          controllers
          new-controllers))

(rf/reg-fx :update-controllers
  (fn [new-controllers]
    (swap! state/controllers update-controllers new-controllers)))