(ns kee-frame.core
  (:require [kee-frame.legacy :as legacy]
            [kee-frame.state :as state]
            [kee-frame.router :as router]
            [kee-frame.log :as log]
            [kee-frame.spec :as spec]
            [re-frame.interop :as interop]
            [clojure.spec.alpha :as s]
            [expound.alpha :as e]))

(def valid-option-key? #{:router :hash-routing? :base-path :routes :process-route :debug? :debug-config
                         :chain-links :app-db-spec :root-component :initial-db :log-spec-error
                         :screen :scroll :route-change-event :not-found :log :global-interceptors})

(defn extra-options
  "Complete listing of invalid options sent to the `start!` function."
  [options]
  (->> options
       (filter (fn [[k]] (not (valid-option-key? k))))
       (into {})))

(defn start!
  "Starts your client application with the specified `options`.

  This function is intentionally forgiving in certain ways:
  - You can call it as often as you want. Figwheel should call it on each code change
  - You can omit the `options` altogether. kee-frame chooses sensible defaults for you and leads the way.

  Usage:
  ```
  (k/start! {:debug?         true
             :routes         my-reitit-routes
             :hash-routing?  true
             :initial-db     {:some-property \"default value\"}
             :root-component [my-reagent-root-component]
             :app-db-spec    :spec/my-db-spec})
  ```"
  [options]
  (log/init! (:log options))
  (when-not (s/valid? ::spec/start-options options)
    (e/expound ::spec/start-options options)
    (throw (ex-info "Invalid options" (s/explain-data ::spec/start-options options))))
  (let [extras (extra-options options)]
    (when (seq extras)
      (throw (ex-info (str "Uknown startup options. Valid keys are " valid-option-key?) extras))))
  (router/start! options))

(def reg-chain legacy/reg-chain)
;(def reg-chain* chain/reg-chain*)
(def reg-chain-named legacy/reg-chain-named)
;(def reg-chain-named* chain/reg-chain-named*)
(def reg-event-fx legacy/reg-event-fx)
(def reg-event-db legacy/reg-event-db)

(defn -replace-controller
  [controllers controller]
  (reduce
   (fn [ret existing-controller]
     (if (= (:id controller)
            (:id existing-controller))
       (conj ret controller)
       (conj ret existing-controller)))
   interop/empty-queue
   controllers))

(defn reg-controller
  "Put a controller config map into the global controller registry.

  Parameters:

  `id`: Must be unique in controllere registry. Will appear in logs.

  `controller`: A map with the following keys:
  - `:params`: A function that receives the route data and returns the part that should be sent to the `start` function. A nil
  return means that the controller should not run for this route.

  - `:start`: A function or an event vector. Called when `params` returns a non-nil value different from the previous
  invocation. The function receives whatever non-nil value that was returned from `params`,
  and returns a re-frame event vector. If the function does nothing but returning the vector, the surrounding function
  can be omitted.

  - `:stop`: Optional. A function or an event vector. Called when previous invocation of `params` returned non-nil and the
  current invocation returned nil. If the function does nothing but returning the vector, the surrounding function
  can be omitted."
  [id controller]
  (let [controller (assoc controller :id id)]
    (when-not (s/valid? ::spec/controller controller)
      (e/expound ::spec/controller controller)
      (throw (ex-info "Invalid controller" (s/explain-data ::spec/controller controller))))
    (swap! state/controllers (fn [controllers]
                               (let [ids (map :id controllers)]
                                 (if (some #{id} ids)
                                   ;; If the id already exists we replace it in-place to maintain the ordering of
                                   ;; controllers esp during hot-code reloading in development.
                                   (-replace-controller controllers controller)
                                   (conj controllers controller)))))))

(defn path-for
  "Make a uri from route data. Useful for avoiding hard coded links in your app.

  Parameters:

  `handler`: The reitit handler from route data

  `params`: Reitit route params for the requested route

  Usage: `[:a {:href (k/path-for [:orders :sort-by :date]} \"Orders sorted by date\"]`"
  [handler & params]
  (apply router/url handler params))

(defn case-route
  "Reagent component that renders different components for different routes.

  Semantics similar to clojure.core/case

  You can include a single default component at the end that serves as the default view

  Parameters:

  `f`: A function that receives the route data on every route change, and returns the value to dispatch on.

  `pairs`: A pair consists of the dispatch value and the reagent component to dispatch to. An optional single default
  component can be added at the end.

  Returns the first component with a matching dispatch value.

  Usage:
  ```
  [k/switch-route (fn [route] (:handler route))
    :index [:div \"This is index page\"]
    :about [:div \"This is the about page\"]
    [:div \"Probably also the index page\"]]
  ```"
  [f & pairs]
  (apply router/case-route f pairs))

(defn switch-route
  "DEPRECATED in favor of case-route

  Reagent component that renders different components for different routes.

  You might need to include a case for `nil`, since there are no route data before the first navigation.

  Parameters:

  `f`: A function that receives the route data on every route change, and returns the value to dispatch on.

  `pairs`: A pair consists of the dispatch value and the reagent component to dispatch to.

  Returns the first component with a matching dispatch value.

  Usage:
  ```
  [k/switch-route (fn [route] (:handler route))
    :index [:div \"This is index page\"]
    :about [:div \"This is the about page\"]
    nil    [:div \"Probably also the index page\"]]
  ```"
  [f & pairs]
  (apply router/switch-route f pairs))
