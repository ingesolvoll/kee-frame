(ns kee-frame.controller
  (:require [re-frame.core :as rf]
            [accountant.core :as accountant]
            [cljs.core.match :refer [match]]
            [bidi.bidi :as bidi]
            [kee-frame.state :as state]))

(defn url [& params]
  (apply bidi/path-for @state/routes params))

(defn goto [route & params]
  (accountant/navigate! (apply url route params)))

(defn process-params [params route]
  (cond
    (vector? params) (get-in route params)
    (ifn? params) (params route)))

(defn do-start [id ctx start params]
  (when start
    (rf/console :log "Starting controller " id " with params " params)
    (cond
      (vector? start) (rf/dispatch (conj start params))
      (ifn? start) (when-let [start-dispatch (start ctx params)]
                     (rf/dispatch start-dispatch)))))

(defn do-stop [id ctx stop]
  (when stop
    (rf/console :log "Stopping controller " id)
    (cond
      (vector? stop) (rf/dispatch stop)
      (ifn? stop) (some-> ctx stop rf/dispatch))))

(defn process-controller [id {:keys [last-params params start stop]} ctx route]
  (let [current-params (process-params params route)]
    (match [last-params current-params (= last-params current-params)]
           [_ _ true] nil
           [nil _ false] (do-start id ctx start current-params)
           [_ nil false] (do-stop id ctx stop)
           [_ _ false] (do (do-stop id ctx stop)
                           (do-start id ctx start current-params)))
    current-params))

(defn apply-route [controllers ctx route]
  (->> controllers
       (map (fn [[id controller]]
              [id (assoc controller :last-params (process-controller id controller ctx route))])
            controllers)
       (into {})))

(defn nav-handler [path]
  (if-let [route (bidi/match-route @state/routes path)]
    (rf/dispatch [::route-changed route])
    (do (rf/console :group "No route match found")
        (rf/console :error "No match found for path " path)
        (rf/console :log "Available routes: " @state/routes)
        (rf/console :groupEnd))))

(defn start-router! [routes]
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
                   (swap! state/controllers apply-route ctx route)
                   {:db (assoc db :kee-frame/route route)}))

(rf/reg-fx :navigate-to #(apply goto %))

(rf/reg-sub :kee-frame/route :kee-frame/route)