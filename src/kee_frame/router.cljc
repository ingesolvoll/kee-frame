(ns kee-frame.router
  (:require [kee-frame.interop :as interop]
            [re-frame.core :as rf]
            [kee-frame.api :as api :refer [dispatch-current! navigate! url->data data->url]]
            [kee-frame.state :as state]
            [kee-frame.controller :as controller]
            [bidi.bidi :as bidi]))

(defn url [data]
  (when-not @state/router
    (throw (ex-info "No router defined for this app" {:router @state/router})))
  (data->url @state/router data))

(defn goto [data]
  (navigate! @state/navigator (url data)))

(defn nav-handler [router]
  (fn [path]
    (if-let [route (url->data router path)]
      (rf/dispatch [::route-changed route])
      (do (rf/console :group "No route match found")
          (rf/console :error "No match found for path " path)
          (rf/console :groupEnd)))))

(defrecord BidiRouter [routes]
  api/Router
  (data->url [_ data]
    (when-not (vector? data)
      (throw (ex-info "Bidi route data is a vector consisting of handler and route params as kw args" {:route data})))
    (or (apply bidi/path-for routes data)
        (throw (ex-info "Could not find path for " data {:routes routes}))))
  (url->data [_ url]
    (or (bidi/match-route routes url)
        (throw (ex-info "Not a valid url" {:url    url
                                           :routes routes})))))

(defn bootstrap-routes [routes router]
  (let [initialized? (boolean @state/navigator)
        router (or router (->BidiRouter routes))]
    (reset! state/router router)
    (rf/reg-fx :navigate-to goto)

    (when-not initialized?
      (reset! state/navigator
              (interop/make-navigator {:nav-handler  (nav-handler router)
                                       :path-exists? #(boolean (url->data router %))})))
    (dispatch-current! @state/navigator)))

(rf/reg-event-db :init (fn [db [_ initial]] (merge initial db)))

(defn reg-route-event []
  (rf/reg-event-fx ::route-changed
                   (if @state/debug? [rf/debug])
                   (fn [{:keys [db] :as ctx} [_ route]]
                     (swap! state/controllers controller/apply-route ctx route)
                     {:db (assoc db :kee-frame/route route)})))

(defn start! [{:keys [routes initial-db router app-db-spec debug? root-component chain-links breakpoints]
               :or   {debug? false}}]
  (reset! state/app-db-spec app-db-spec)
  (reset! state/debug? debug?)
  (when chain-links
    (swap! state/links concat chain-links))

  (reg-route-event)
  (when (and routes router)
    (throw (ex-info "Both routes and router specified. If you want to use these routes, pass them to your router constructor."
                    {:routes routes
                     :router router})))
  (when (or routes router)
    (bootstrap-routes routes router))

  (when initial-db
    (rf/dispatch-sync [:init initial-db]))

  (interop/set-breakpoints breakpoints)

  (rf/reg-sub :kee-frame/route (fn [db] (:kee-frame/route db nil)))
  (interop/render-root root-component))

(defn make-route-component [component route]
  (if (fn? component)
    [component route]
    component))

(defn switch-route [f & pairs]
  (when-not (even? (count pairs))
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
        (throw (ex-info "Could not find a component to match route. Did you remember to include a case for nil?"
                        {:route          @route
                         :dispatch-value dispatch-value
                         :pairs          pairs}))))))