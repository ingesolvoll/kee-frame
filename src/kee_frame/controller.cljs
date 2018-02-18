(ns kee-frame.controller
  (:require [re-frame.core :as rf]
            [accountant.core :as accountant]
            [cljs.core.match :refer [match]]
            [bidi.bidi :as bidi]
            [kee-frame.state :as state]))

(defn process-params [params route]
  (cond
    (vector? params) (get-in route params)
    (ifn? params) (params route)))

(defn do-dispatch [ctx dispatch]
  (cond
    (vector? dispatch) (rf/dispatch dispatch)
    (ifn? dispatch) (some-> ctx dispatch rf/dispatch)))

(defn do-start [id ctx start]
  (when start
    (rf/console :log "Starting controller " id)
    (do-dispatch ctx start)))

(defn do-stop [id ctx stop]
  (when stop
    (rf/console :log "Stopping controller " id)
    (do-dispatch ctx stop)))

(defn process-controller [id {:keys [last-params params start stop]} ctx route]
  (let [current-params (process-params params route)]
    (match [last-params current-params (= last-params current-params)]
           [_ _ true] nil
           [nil _ false] (do-start id ctx start)
           [_ nil false] (do-stop id ctx stop)
           [_ _ false] (do (do-stop id ctx stop)
                           (do-start id ctx start)))
    current-params))

(defn apply-route [controllers ctx route]
  (->> controllers
       (map (fn [[id controller]]
              [id (assoc controller :last-params (process-controller id controller ctx route))])
            controllers)
       (into {})))

(defn legacy-nav-handler [path]
  (if-let [{:keys [handler route-params]} (bidi/match-route @state/routes path)]
    (let [current-page (-> handler namespace keyword)
          secondary-page (-> handler name keyword)
          route {:current-page   current-page
                 :secondary-page secondary-page
                 :route-params   route-params}]
      (rf/dispatch [:route route])
      (rf/dispatch [:route-changed route]))
    (do (println "No match found for path " path)
        (cljs.pprint/pprint @state/routes))))

(defn nav-handler [path]
  (if-let [route (bidi/match-route @state/routes path)]
    (rf/dispatch [:route-changed route])
    (do (rf/console :group "No route match found")
        (rf/console :error "No match found for path " path)
        (rf/console :log "Available routes: " @state/routes)
        (rf/console :groupEnd))))

(defn start-router! [routes]
  (let [initialized? (boolean @state/routes)]
    (reset! state/routes routes)
    (when-not initialized?
      (accountant/configure-navigation!
        {:nav-handler  #(legacy-nav-handler %)
         :path-exists? #(boolean (bidi/match-route @state/routes %))}))
    (accountant/dispatch-current!)))

(rf/reg-event-db :route-changed
                 [rf/debug]
                 (fn [{:keys [db] :as ctx} [_ route]]
                   (swap! state/controllers apply-route ctx route)
                   (assoc db :route route)))