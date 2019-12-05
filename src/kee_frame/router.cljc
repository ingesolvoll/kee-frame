(ns ^:no-doc kee-frame.router
  (:require [kee-frame.interop :as interop]
            [re-frame.core :as rf]
            [re-chain.core :as chain]
            [kee-frame.api :as api :refer [dispatch-current! navigate! url->data data->url]]
            [kee-frame.state :as state]
            [kee-frame.scroll :as scroll]
            [kee-frame.controller :as controller]
            [reitit.core :as reitit]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [kee-frame.spec :as spec]
            [expound.alpha :as e]))

(def default-chain-links [{:effect-present? (fn [effects] (:http-xhrio effects))
                           :get-dispatch    (fn [effects] (get-in effects [:http-xhrio :on-success]))
                           :set-dispatch    (fn [effects dispatch] (assoc-in effects [:http-xhrio :on-success] dispatch))}])

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

(s/def ::reitit-route-data (s/cat :route-name keyword? :path-params (s/* (s/map-of keyword? any?))))

(defn assert-route-data [data]
  (when-not (s/valid? ::reitit-route-data data)
    (e/expound ::reitit-route-data data)
    (throw (ex-info "Bad route data input" (s/explain-data ::reitit-route-data data)))))

(defn url-not-found [routes data]
  (throw (ex-info "Could not find url for the provided data"
                  {:routes routes
                   :data   data})))

(defn route-match-not-found [routes url]
  (throw (ex-info "No match for URL in routes"
                  {:url    url
                   :routes routes})))

(defn match-data [routes route hash?]
  (let [[_ path-params] route]
    (str (when hash? "/#") (:path (apply reitit/match-by-name routes route))
         (when-some [q (:query-string path-params)] (str "?" q))
         (when-some [h (:hash path-params)] (str "#" h)))))

(defn match-url [routes url]
  (let [[path+query fragment] (-> url (str/replace #"^/#/" "/") (str/split #"#" 2))
        [path query] (str/split path+query #"\?" 2)]
    (some-> (reitit/match-by-path routes path)
            (assoc :query-string query :hash fragment))))

(defrecord ReititRouter [routes hash?]
  api/Router
  (data->url [_ data]
    (assert-route-data data)
    (or (match-data routes data hash?)
        (url-not-found routes data)))
  (url->data [_ url]
    (or (match-url routes url)
        (route-match-not-found routes url))))

(defn bootstrap-routes [routes router hash-routing? scroll]
  (let [initialized? (boolean @state/navigator)
        router (or router (->ReititRouter (reitit/router routes) hash-routing?))]
    (reset! state/router router)
    (rf/reg-fx :navigate-to goto)

    (when-not initialized?
      (when scroll (scroll/start!))
      (reset! state/navigator
              (interop/make-navigator {:nav-handler  (nav-handler router)
                                       :path-exists? #(boolean (url->data router %))})))
    (dispatch-current! @state/navigator)))

(rf/reg-event-db :init (fn [db [_ initial]] (merge initial db)))


(defn reg-route-event [scroll]
  (rf/reg-event-fx ::route-changed
                   (if @state/debug? [rf/debug])
                   (fn [{:keys [db] :as ctx} [_ route]]
                     (when scroll
                       (scroll/monitor-requests! route))
                     (swap! state/controllers controller/apply-route ctx route)
                     (merge {:db (assoc db :kee-frame/route route)}
                            (when scroll
                              {:dispatch-later [{:ms       50
                                                 :dispatch [::scroll/poll route 0]}]})))))

(defn start! [{:keys [routes initial-db router hash-routing? app-db-spec debug? root-component chain-links screen scroll]
               :or   {debug? false
                      scroll true}}]
  (reset! state/app-db-spec app-db-spec)
  (reset! state/debug? debug?)
  (chain/configure! (concat default-chain-links
                            chain-links))

  (reg-route-event scroll)
  (when (and routes router)
    (throw (ex-info "Both routes and router specified. If you want to use these routes, pass them to your router constructor."
                    {:routes routes
                     :router router})))
  (when (or routes router)
    (bootstrap-routes routes router hash-routing? scroll))

  (when initial-db
    (rf/dispatch-sync [:init initial-db]))

  (when screen
    (let [config (when-not (boolean? screen) screen)]
      (if @state/breakpoints-initialized?
        (interop/set-breakpoint-subs config)
        (do (interop/set-breakpoints config)
            (reset! state/breakpoints-initialized? true)))))

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