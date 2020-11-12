(ns kee-frame.fsm.http
  (:require [statecharts.core :as fsm]
            [statecharts.integrations.re-frame :as fsm.rf]
            [re-frame.core :as f]))

(defn update-retries [state & _]
  (update state :retries inc))

(defn reset-retries [state & _]
  (assoc state :retries 0))

(defn more-retries? [max-retries {:keys [retries]} _]
  (< retries max-retries))

(defn store-error [state event]
  (assoc state :error (:data event)))

(defn http-fsm [{:keys [id init-event transition-event max-retries] :as config}]
  {:id           id
   :initial      ::loading
   :states       {::loading {:entry #(f/dispatch [::load config])
                             :on    {::error   ::error
                                     ::success ::loaded}}
                  ::error   {:initial (if (< 0 max-retries)
                                        ::retrying
                                        ::halted)
                             :entry (fsm/assign store-error)
                             :states  {::retrying {:initial ::waiting
                                                   :entry   (fsm/assign reset-retries)
                                                   :states  {::loading {:entry [(fsm/assign update-retries)
                                                                                #(f/dispatch [::load config])]
                                                                        :on    {::error [{:guard  (partial more-retries? max-retries)
                                                                                          :target ::waiting}
                                                                                         [:> ::error ::halted]]}}
                                                             ::waiting {:after [{:delay  2000
                                                                                 :target ::loading}]}}}
                                       ::halted   {}}}
                  ::loaded  {}}
   :integrations {:re-frame {:path             (f/path [:fsm id])
                             :initialize-event init-event
                             :transition-event transition-event}}})

(defn ns-key [id v]
  (keyword (name id) v))

(f/reg-event-fx ::on-failure
  (fn [_ [_ transition-event error]]
    {:dispatch [transition-event ::error error]}))

(f/reg-event-fx ::on-success
  (fn [_ [_ {:keys [transition-event on-success]} data]]
    {:dispatch-n [[transition-event ::success]
                  (conj on-success data)]}))

(f/reg-event-fx ::load
  (fn [_ [_ {:keys [transition-event http-xhrio] :as config}]]
    {:http-xhrio (merge http-xhrio
                        {:on-failure [::on-failure transition-event]
                         :on-success [::on-success config]})}))

(f/reg-fx ::http-fsm
  (fn [{:keys [id] :as config}]
    (let [init-event       (ns-key id "init")
          transition-event (ns-key id "transition")
          config           (merge config {:init-event       init-event
                                          :transition-event transition-event})]

      (-> config
          http-fsm
          fsm/machine
          fsm.rf/integrate)
      (f/dispatch [init-event]))))

(f/reg-event-fx ::restart
  (fn [_ [_ id]]
    (let [init-event (ns-key id "init")]
      {:dispatch [init-event]})))

(f/reg-event-fx ::http-fsm
  ;; Starts the interceptor for the given fsm.
  (fn [_ [_ fsm]]
    {::http-fsm fsm}))