(ns kee-frame.fsm.statecharts
  (:require
   [re-frame.core :as f]
   [statecharts.clock :as clock]
   [statecharts.core :as fsm]
   [statecharts.integrations.re-frame :as sc.rf]
   [statecharts.utils :as u]))

(defonce epochs (volatile! {}))

(defn new-epoch [id]
  (get (vswap! epochs update id sc.rf/safe-inc) id))

(f/reg-event-db
 ::init
 (fn [db [_ {:keys [id] :as machine} initialize-args]]
   (let [{:keys [epoch?]} (get-in machine [:integrations :re-frame])
         new-state (cond-> (fsm/initialize machine initialize-args)
                     epoch?
                     (assoc :_epoch (new-epoch id)))]
     (assoc-in db [:fsm id] new-state))))


(f/reg-event-db
 ::transition
 (fn [db [_ {:keys [epoch? id] :as machine} fsm-event data :as args]]
   (let [fsm-event (u/ensure-event-map fsm-event)
         more-data (when (> (count args) 3)
                     (subvec args 2))]
     (if (and epoch? (sc.rf/should-discard fsm-event (:_epoch db)))
       (do
         (sc.rf/log-discarded-event fsm-event)
         db)
       (update-in db [:fsm id] (partial fsm/transition machine) (cond-> (assoc fsm-event :data data)
                                                                  (some? more-data)
                                                                  (assoc :more-data more-data)))))))

(defn integrate
  ([machine]
   (integrate machine sc.rf/default-opts))
  ([machine {:keys [clock]}]
   (let [clock   (or clock (clock/wall-clock))
         machine (assoc machine :scheduler (sc.rf/make-rf-scheduler ::transition clock))]
     (f/reg-global-interceptor (f/->interceptor
                                :id (:id machine)
                                :before (fn [ctx]
                                          (if (some-> ctx :coeffects :event first (= ::transition))
                                            (update-in ctx [:coeffects :event] (fn [event]
                                                                                 (vec (concat [(first event) machine] (rest event)))))
                                            ctx))))
     (f/dispatch [::init machine]))))
