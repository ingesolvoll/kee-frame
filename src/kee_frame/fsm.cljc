(ns kee-frame.fsm)

(defn valid-form? [db]
  (and (:first-name db)
       (:last-name db)))

(def invalid-form? (complement valid-form?))

(defn save-form-event [_ _]
  {:http-xhrio {:method :get
                :url    "http://www.vg.no"}})

(defn reg-fsm [id fsm]
  )

(defn saved-event [_ _])

(defn http-error [_ _])

(reg-fsm :save-button
         {:disabled {:next {[:db valid-form?] :enabled}}
          :enabled  {:next {[:db invalid-form?]            :disabled
                            [:event :save save-form-event] :saving}}
          :saving   {:on-enter (fn [db] (assoc db :progress? true
                                                  :hide-something? true))
                     :on-leave (fn [db] (assoc db :progress? false))
                     :next     {[:event :save-success saved-event] :saved
                                [:event :save-error http-error]    :error
                                [:timeout 5000]                    :error}}
          :saved    {:next {[:timeout 3000] :disabled}}
          :error    {:next {[:timeout 5000] :disabled}}})