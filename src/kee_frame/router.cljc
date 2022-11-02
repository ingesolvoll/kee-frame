(ns ^:no-doc kee-frame.router
  (:require [re-frame.core :as rf :refer [console]]
            [re-chain.core :as chain]
            [kee-frame.event-logger :as event-logger]
            [kee-frame.api :as api :refer [dispatch-current! navigate! url->data data->url]]
            [kee-frame.interop :as interop]
            [kee-frame.spec :as spec]
            [kee-frame.state :as state]
            [kee-frame.scroll :as scroll]
            [kee-frame.controller :as controller]
            [reitit.core :as reitit]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [expound.alpha :as e]
            [clojure.set :as set]))

(def default-chain-links [{:effect-present? (fn [effects] (:http-xhrio effects))
                           :get-dispatch    (fn [effects] (get-in effects [:http-xhrio :on-success]))
                           :set-dispatch    (fn [effects dispatch] (assoc-in effects [:http-xhrio :on-success] dispatch))}])

(defn url [data]
  (when-not @state/router
    (throw (ex-info "No router defined for this app" {:router @state/router})))
  (data->url @state/router data))

(defn goto [data]
  (navigate! @state/navigator (url data)))

(defn nav-handler [router route-change-event]
  (fn [path]
    (if-let [route (url->data router path)]
      (rf/dispatch [(or route-change-event ::route-changed) route])
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

(defn valid? [{:keys [path-params required]}]
  (set/subset? required (set (keys path-params))))

(defn match-data [routes route hash? base-path]
  (let [[_ path-params] route
        {:keys [path] :as match} (apply reitit/match-by-name routes route)]
    (when (valid? match)
      (str base-path (when hash? "/#") path
           (when-some [q (:query-string path-params)] (str "?" q))
           (when-some [h (:hash path-params)] (str "#" h))))))

(defn- remove-base-path [url base-path]
  ;; i don't want to think about how to quote the regex
  ;; also we would pay for compilation on every check
  (if (str/starts-with? url base-path)
    (subs url (count base-path))
    url))

(defn match-url [routes base-path url]
  (let [[path+query fragment] (-> url
                                  (remove-base-path base-path)
                                  (str/replace #"^/#/" "/")
                                  (str/split #"#" 2))
        [path query] (str/split path+query #"\?" 2)]
    (some-> (reitit/match-by-path routes path)
            (assoc :query-string query :hash fragment))))

(defrecord ReititRouter [routes hash? base-path not-found]
  api/Router
  (data->url [_ data]
    (assert-route-data data)
    (or (match-data routes data hash? base-path)
        (url-not-found routes data)))
  (url->data [_ url]
    (or (match-url routes base-path url)
        (some->> not-found (match-url routes base-path))
        (route-match-not-found routes url))))

(defn bootstrap-routes [{:keys [routes router hash-routing? base-path scroll route-change-event not-found]}]
  (let [initialized? (boolean @state/navigator)
        router (or router (->ReititRouter (reitit/router routes) hash-routing? base-path not-found))]
    (reset! state/router router)
    (rf/reg-fx :navigate-to goto)

    (when-not initialized?
      (when scroll (scroll/start!))
      (reset! state/navigator
              (interop/make-navigator {:nav-handler  (nav-handler router route-change-event)
                                       :path-exists? #(boolean (url->data router %))})))
    (dispatch-current! @state/navigator)))

(rf/reg-event-db :init (fn [db [_ initial]] (merge initial db)))

(defn reg-route-event [scroll]
  (rf/reg-event-fx ::route-changed
    [event-logger/interceptor]
    (fn [{:keys [db] :as ctx} [_ route]]
      (when scroll
        (scroll/monitor-requests! route))
      (let [{:keys [update-controllers dispatch-n]} (controller/controller-effects @state/controllers ctx route)]
        (cond-> {:db             (assoc db :kee-frame/route route)
                 :dispatch-later [(when scroll
                                    {:ms       50
                                     :dispatch [::scroll/poll route 0]})]}
          dispatch-n (assoc :dispatch-n dispatch-n)
          update-controllers (assoc :update-controllers update-controllers))))))

(defn deprecations [{:keys [debug? debug-config]}]
  (when (not (nil? debug?))
    (console :warn "Kee-frame option :debug? has been removed. Configure timbre logger through :log option instead. Example: {:level :debug :ns-blacklist [\"kee-frame.event-logger\"]}"))

  (when (not (nil? debug-config))
    (console :warn "Kee-frame option :debug-config has been removed. Configure timbre logger through :log option instead. Example: {:level :debug :ns-blacklist [\"kee-frame.event-logger\"]}")))

(defn start! [{:keys [routes initial-db router app-db-spec root-component chain-links
                      screen scroll global-interceptors log-spec-error base-path]
               :or   {scroll true base-path ""}
               :as   config}]
  (deprecations config)
  (when app-db-spec
    (rf/reg-global-interceptor (spec/spec-interceptor app-db-spec log-spec-error)))
  (doseq [i global-interceptors]
    (rf/reg-global-interceptor i))

  (chain/configure! (concat default-chain-links
                            chain-links))

  (reg-route-event scroll)
  (when (and routes router)
    (throw (ex-info "Both routes and router specified. If you want to use these routes, pass them to your router constructor."
                    {:routes routes
                     :router router})))
  (when (or routes router)
    (bootstrap-routes (assoc config :scroll scroll :base-path base-path)))

  (when initial-db
    (rf/dispatch-sync [:init initial-db]))

  (when screen
    (let [config (when-not (boolean? screen) screen)]
      (if @state/breakpoints-initialized?
        (interop/set-breakpoint-subs config)
        (do (interop/set-breakpoints config)
            (reset! state/breakpoints-initialized? true)))))

  (rf/reg-sub :kee-frame/route (fn [db _] (:kee-frame/route db nil)))
  (interop/render-root root-component))

(defn make-route-component [component route]
  (if (fn? component)
    [component route]
    component))

(defn case-route [f & pairs]
  (let [route          (rf/subscribe [:kee-frame/route])
        dispatch-value (f @route)]
    (loop [[first-pair & rest-pairs] (partition-all 2 pairs)]
      (cond

        (some-> first-pair seq count (= 2))
        (let [[value component] first-pair]
          (if (= value dispatch-value)
            (make-route-component component @route)
            (recur rest-pairs)))

        (some-> first-pair seq count (= 1))
        (make-route-component (first first-pair) @route)

        :else
        (throw (ex-info "Could not find a component to match route. Did you remember to include a single last default case?"
                        {:route          @route
                         :dispatch-value dispatch-value
                         :pairs          pairs}))))))

(defn switch-route [f & pairs]
  (when-not (even? (count pairs))
    (throw (ex-info "switch-route accepts an even number of args" {:pairs       pairs
                                                                   :pairs-count (count pairs)})))
  (let [route          (rf/subscribe [:kee-frame/route])
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
