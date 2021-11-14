(ns kee-frame.fsm.beta
  (:require
   [glimt.core :as http]
   [re-frame.core :as f]
   [statecharts.clock :as clock]
   [statecharts.core :as fsm]
   [statecharts.core :as fsm]
   [statecharts.integrations.re-frame :as sc.rf]
   [statecharts.utils :as u]
   [taoensso.timbre :as log])
  (:require-macros kee-frame.fsm.beta))

(defonce epochs (volatile! {}))

(defn new-epoch [id]
  (get (vswap! epochs update id sc.rf/safe-inc) id))

(f/reg-event-db
 ::init
 (fn [db [_ {:keys [id] :as machine} initialize-args]]
   (when-not (get-in db [:fsm id])
     (let [new-state (-> (fsm/initialize machine initialize-args)
                         (assoc :_epoch (new-epoch id)))]
       (assoc-in db [:fsm id] new-state)))))


(defn reg-transition-event [{:keys [epoch? id] :as machine} opts]
  (f/reg-event-db
   [::transition id]
   (fn [db [_ fsm-event data :as args]]
     (when (get-in db [:fsm id])
       (let [fsm-event (u/ensure-event-map fsm-event)
             more-data (when (> (count args) 3)
                         (subvec args 2))]
         (if (and epoch?
                  (sc.rf/should-discard fsm-event (get-in db [:fsm id :_epoch])))
           (do
             (sc.rf/log-discarded-event fsm-event)
             db)
           (update-in db [:fsm id]
                      (partial fsm/transition machine)
                      (cond-> (assoc fsm-event :data data)
                        (some? more-data)
                        (assoc :more-data more-data))
                      opts)))))))

(defn integrate
  ([machine]
   (integrate machine sc.rf/default-opts))
  ([{:keys [id] :as machine} {:keys [clock] :as opts}]
   (let [clock   (or clock (clock/wall-clock))
         machine (assoc machine :scheduler (sc.rf/make-rf-scheduler [::transition id] clock))]
     (reg-transition-event machine (:transition-opts opts))
     (f/dispatch [::init machine]))))


(defn http [config]
  (http/embedded-fsm (assoc config :transition-event [::transition (:id config)]
                                   :init-event ::init)))


(f/reg-fx ::start
  (fn [fsm]
    (-> fsm
        fsm/machine
        (integrate {:transition-opts {:ignore-unknown-event? true}}))))

(f/reg-event-fx ::http
                (fn [_ [_ config]]
                  {::http/start config}))

(f/reg-event-fx ::start
                (fn [_ [_ fsm]]
                  {::start fsm}))

(f/reg-event-fx ::stop
                (fn [{db :db} [_ id]]
                  {:db (update db :fsm dissoc id)}))

(f/reg-sub ::state
  (fn [db [_ id]]
    (get-in db [:fsm id :_state])))

(f/reg-sub ::state-full
  (fn [db [_ id]]
    (get-in db [:fsm id])))

(defn match-state [state & pairs]
  (loop [[first-pair & rest-pairs] (partition-all 2 pairs)]
    (cond

      (some-> first-pair seq count (= 2))
      (let [[value component] first-pair]
        (if (fsm/matches state value)
          component
          (recur rest-pairs)))

      (some-> first-pair seq count (= 1))
      (first first-pair)

      :else
      (throw (ex-info "Could not find a component to match state."
                      {:state state
                       :pairs pairs})))))
