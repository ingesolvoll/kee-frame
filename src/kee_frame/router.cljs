(ns kee-frame.router
  (:require [re-frame.core :as rf]
            [kee-frame.state :as state]
            [kee-frame.controller :as controller]
            [accountant.core :as accountant]
            [bidi.bidi :as bidi]))

(defn url [& params]
  (apply bidi/path-for @state/routes params))

(defn goto [route & params]
  (accountant/navigate! (apply url route params)))

(defn nav-handler [postprocess-route]
  (fn [path]
    (if-let [route (->> path
                        (bidi/match-route @state/routes)
                        postprocess-route)]
      (rf/dispatch [::route-changed route])
      (do (rf/console :group "No route match found")
          (rf/console :error "No match found for path " path)
          (rf/console :log "Available routes: " @state/routes)
          (rf/console :groupEnd)))))

(rf/reg-event-db :init (fn [db [_ initial]] (merge initial db)))

(defn start! [{:keys [routes initial-db postprocess-route app-db-spec debug? interceptors]
               :or   {postprocess-route identity
                      initial-db        {}
                      debug?            false}}]
  (let [initialized? (boolean @state/routes)]
    (reset! state/routes routes)
    (reset! state/app-db-spec app-db-spec)
    (reset! state/debug? debug?)

    (rf/dispatch-sync [:init initial-db])

    (rf/reg-event-fx ::route-changed
                     interceptors
                     (fn [{:keys [db] :as ctx} [route]]
                       (swap! state/controllers controller/apply-route ctx route)
                       {:db (assoc db :kee-frame/route route)}))

    (rf/reg-fx :navigate-to  #(apply goto %))

    (rf/reg-sub :kee-frame/route :kee-frame/route)

    (when-not initialized?
      (accountant/configure-navigation!
        {:nav-handler  (nav-handler postprocess-route)
         :path-exists? #(boolean (bidi/match-route @state/routes %))}))
    (accountant/dispatch-current!)))