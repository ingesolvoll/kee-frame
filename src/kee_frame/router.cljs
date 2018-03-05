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

(defn nav-handler [path]
  (if-let [route (bidi/match-route @state/routes path)]
    (rf/dispatch [::route-changed route])
    (do (rf/console :group "No route match found")
        (rf/console :error "No match found for path " path)
        (rf/console :log "Available routes: " @state/routes)
        (rf/console :groupEnd))))

(defn start! [routes]
  (let [initialized? (boolean @state/routes)]
    (reset! state/routes routes)
    (when-not initialized?
      (accountant/configure-navigation!
        {:nav-handler  #(nav-handler %)
         :path-exists? #(boolean (bidi/match-route @state/routes %))}))
    (accountant/dispatch-current!)))

(rf/reg-event-fx ::route-changed
                 [rf/debug]
                 (fn [{:keys [db] :as ctx} [_ route]]
                   (swap! state/controllers controller/apply-route ctx route)
                   {:db (assoc db :kee-frame/route route)}))

(rf/reg-fx :navigate-to #(apply goto %))

(rf/reg-sub :kee-frame/route :kee-frame/route)