(ns kee-frame.router
  (:require [re-frame.core :as rf]
            [kee-frame.state :as state]
            [kee-frame.controller :as controller]
            [accountant.core :as accountant]
            [bidi.bidi :as bidi]
            [reagent.core :as reagent]))

(defn url [& params]
  (apply bidi/path-for @state/routes params))

(defn goto [route & params]
  (accountant/navigate! (apply url route params)))

(defn nav-handler [process-route]
  (fn [path]
    (if-let [route (->> path
                        (bidi/match-route @state/routes)
                        process-route)]
      (rf/dispatch [::route-changed route])
      (do (rf/console :group "No route match found")
          (rf/console :error "No match found for path " path)
          (rf/console :log "Available routes: " @state/routes)
          (rf/console :groupEnd)))))

(rf/reg-event-db :init (fn [db [_ initial]] (merge initial db)))

(defn start! [{:keys [routes initial-db process-route app-db-spec debug? root-component]
               :or   {process-route identity
                      initial-db    {}
                      debug?        false}}]
  (let [initialized? (boolean @state/routes)]
    (reset! state/routes routes)
    (reset! state/app-db-spec app-db-spec)
    (reset! state/debug? debug?)

    (rf/dispatch-sync [:init initial-db])

    (rf/reg-event-fx ::route-changed
                     (if debug? [rf/debug])
                     (fn [{:keys [db] :as ctx} [_ route]]
                       (swap! state/controllers controller/apply-route ctx route)
                       {:db (assoc db :kee-frame/route route)}))

    (rf/reg-fx :navigate-to #(apply goto %))

    (rf/reg-sub :kee-frame/route :kee-frame/route)

    (when root-component
      (reagent/render root-component
                      (.getElementById js/document "app")))

    (when-not initialized?
      (accountant/configure-navigation!
        {:nav-handler  (nav-handler process-route)
         :path-exists? #(boolean (bidi/match-route @state/routes %))}))
    (accountant/dispatch-current!)))