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

(defn start! [ctx start params]
  (when start
    (cond
      (vector? start) start
      (ifn? start) (validate-and-dispatch! (start ctx params)))))

(defn stop! [ctx stop]
  (cond
    (vector? stop) stop
    (ifn? stop) (validate-and-dispatch! (stop ctx))))

(defn process-controller [id {:keys [last-params params start stop]} ctx route]
  (let [current-params (process-params params route)]
    {:id         id
     :params     current-params
     :dispatch-n (match [last-params current-params (= last-params current-params)]
                        [_ _ true] []
                        [nil _ false] [(start! ctx start current-params)]
                        [_ nil false] [(stop! ctx stop)]
                        [_ _ false] [(stop! ctx stop)
                                     (start! ctx start current-params)])}))

(defn apply-route [controllers ctx route]
  (map (fn [[id controller]]
         (process-controller id controller ctx route))
       controllers))

(rf/reg-fx :update-controllers
  (fn [updates]
    (swap! state/controllers (fn [controllers]
                               (->> updates
                                    (reduce (fn [controllers {:keys [id params]}]
                                              (assoc-in controllers [id :last-params] params))
                                            controllers))))))