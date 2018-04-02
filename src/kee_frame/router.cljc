(ns kee-frame.router
  (:require #?@(:cljs [[accountant.core :as accountant]
                       [reagent.core :as reagent]])
            [re-frame.core :as rf]
            [kee-frame.state :as state]
            [kee-frame.controller :as controller]
            [bidi.bidi :as bidi]))

(defprotocol Router
  (dispatch-current! [_])
  (navigate! [_ url])
  (init! [_ opts]))

(defrecord TestRouter [url]
  Router
  (dispatch-current! [{:keys [url]}]
    (prn "dispatch" url))
  (navigate! [this url]
    (prn "navigate!")
    (assoc this :url url))
  (init! [_ opts]
    (prn "init!")))

#?(:cljs
   (defrecord AccountantRouter []
     Router
     (dispatch-current! [_]
       (accountant/dispatch-current!))
     (navigate! [_ url]
       (accountant/navigate! url))
     (init! [_ {:keys [nav-handler path-exists?] :as opts}]
       (accountant/configure-navigation! opts))))

(defn make-router
  [router-type]
  (case router-type
    #?@(:cljs [:accountant (->AccountantRouter)])
    :test (->TestRouter "/")))

(defn url [& params]
  (when-not @state/routes
    (throw (ex-info "No routes defined for this app" {:routes @state/routes})))
  (apply bidi/path-for @state/routes params))

(defn goto [route router & params]
  (navigate! router (apply url route params)))

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

(defn bootstrap-routes [routes router process-route]
  (let [initialized? (boolean @state/routes)]
    (reset! state/routes routes)
    (rf/reg-fx :navigate-to #(apply goto router %))

    (when-not initialized?
      (init! router
             {:nav-handler  (nav-handler process-route)
              :path-exists? #(boolean (bidi/match-route @state/routes %))})
      (reset! state/router router))
    (dispatch-current! router)))

(rf/reg-event-db :init (fn [db [_ initial]] (merge initial db)))

(defn reg-route-event []
  (rf/reg-event-fx ::route-changed
                   (if @state/debug? [rf/debug])
                   (fn [{:keys [db] :as ctx} [_ route]]
                     (swap! state/controllers controller/apply-route ctx route)
                     {:db (assoc db :kee-frame/route route)})))

(defn start! [{:keys [routes router-type initial-db process-route app-db-spec debug? root-component]
               :or   {process-route identity
                      debug?        false
                      router-type   #?(:clj :test
                                       :cljs :accountant)}}]
  (reset! state/app-db-spec app-db-spec)
  (reset! state/debug? debug?)
  (when-let [router (and routes (make-router router-type))]
    (bootstrap-routes routes router process-route))

  (when initial-db
    (rf/dispatch-sync [:init initial-db]))

  (reg-route-event)

  (rf/reg-sub :kee-frame/route (fn [db] (:kee-frame/route db nil)))

  #?(:cljs
     (when root-component
       (if-let [app-element (.getElementById js/document "app")]
         (reagent/render root-component
                         app-element)
         (throw (ex-info "Could not find element with id 'app' to mount app into" {:component root-component}))))))

(defn make-route-component [component route]
  (if (fn? component)
    [component route]
    component))

(defn switch-route [f & pairs]
  (when-not (= 0 (mod (count pairs) 2))
    (throw (ex-info "switch-route accepts an even number of args" {:pairs       pairs
                                                                   :pairs-count (count pairs)})))
  (let [route (rf/subscribe [:kee-frame/route])
        dispatch-value (f @route)]
    (loop [[first-pair & rest-pairs] (partition 2 pairs)]
      (if first-pair
        (let [[value component] first-pair]
          (if (= value dispatch-value)
            (make-route-component component @route)
            (recur rest-pairs)))
        (throw (ex-info "Could not find a component to match route" {:route          @route
                                                                     :dispatch-value dispatch-value
                                                                     :pairs          pairs}))))))
