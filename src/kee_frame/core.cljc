(ns kee-frame.core
  (:require [kee-frame.state :as state]
            [kee-frame.router :as router]
            [re-chain.core :as chain]
            [re-frame.core :as rf :refer [console]]
            [kee-frame.spec :as spec :refer [spec-interceptor]]
            [kee-frame.debug :refer [debug-interceptor]]
            [clojure.spec.alpha :as s]
            [expound.alpha :as e]))

;; Interceptors used by all chains and events registered through kee-frame
(def kee-frame-interceptors [(spec-interceptor state/app-db-spec) (debug-interceptor state/debug?) rf/trim-v])

(def valid-option-key? #{:router :hash-routing? :routes :process-route :debug?
                         :chain-links :app-db-spec :root-component :initial-db
                         :screen :scroll})

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
  (when-not (s/valid? ::spec/start-options options)
    (e/expound ::spec/start-options options)
    (throw (ex-info "Invalid options" (s/explain-data ::spec/start-options options))))
  (let [extras (extra-options options)]
    (when (seq extras)
      (throw (ex-info (str "Uknown startup options. Valid keys are " valid-option-key?) extras))))
  (router/start! options))

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
  (when-not (s/valid? ::spec/controller controller)
    (e/expound ::spec/controller controller)
    (throw (ex-info "Invalid controller" (s/explain-data ::spec/controller controller))))
  (when (get @state/controllers id)
    (console :warn "Overwriting controller with id " id))
  (swap! state/controllers update id merge controller))

(defn reg-event-fx
  "Exactly same signature as `re-frame.core/reg-event-fx`. Use this version if you want kee-frame logging and spec validation.

  `re-frame.core/trim-v` interceptor is also applied."
  ([id handler] (reg-event-fx id nil handler))
  ([id interceptors handler] (rf/reg-event-fx id (concat kee-frame-interceptors interceptors) handler)))

(defn reg-event-db
  "Exactly same signature as `re-frame.core/reg-event-db`. Use this version if you want kee-frame logging and spec validation.

  `re-frame.core/trim-v` interceptor is also applied."
  ([id handler] (reg-event-db id nil handler))
  ([id interceptors handler] (rf/reg-event-db id (concat kee-frame-interceptors interceptors) handler)))

(defn reg-chain-named
  "Same as `reg-chain`, but with manually named event handlers. Useful when you need more meaningful names in your
  event log.

  Parameters:

  `handlers`: pairs of id and event handler.

  Usage:
  ```
  (k/reg-chain-named

    :load-customer-data
    (fn [ctx [customer-id]]
      {:http-xhrio {:uri \"...\"}})

    :receive-customer-data
     (fn [ctx [customer-id customer-data]]
      (assoc-in ctx [:db :customers customer-id] customer-data)))
  ```"
  [& handlers]
  (apply chain/reg-chain-named* kee-frame-interceptors handlers))

(defn reg-chain
  "Register a list of re-frame fx handlers, chained together.

  The chaining is done through dispatch inference. https://github.com/Day8/re-frame-http-fx is supported by default,
  you can easily add your own like this: https://github.com/ingesolvoll/kee-frame#configuring-chains-since-020.

  Each handler's event vector is prepended with accumulated event vectors of previous handlers. So if the first handler
  receives [a b], and the second handler normally would receive [c], it will actually receive [a b c]. The purpose is
  to make all context available to the entire chain, without a complex framework or crazy scope tricks.

  Parameters:

  `id`: the id of the first re-frame event. The next events in the chain will get the same id followed by an index, so
  if your id is `add-todo`, the next one in chain will be called `add-todo-1`.

  `handlers`: re-frame event handler functions, registered with `kee-frame.core/reg-event-fx`.


  Usage:
  ```
  (k/reg-chain
    :load-customer-data

    (fn [ctx [customer-id]]
      {:http-xhrio {:uri    (str \"/customer/\" customer-id)
                    :method :get}})

    (fn [cxt [customer-id customer-data]
      (assoc-in ctx [:db :customers customer-id] customer-data)))
  ```"
  [id & handlers]
  (apply chain/reg-chain* id kee-frame-interceptors handlers))

(defn path-for
  "Make a uri from route data. Useful for avoiding hard coded links in your app.

  Parameters:

  `handler`: The reitit handler from route data

  `params`: Reitit route params for the requested route

  Usage: `[:a {:href (k/path-for [:orders :sort-by :date]} \"Orders sorted by date\"]`"
  [handler & params]
  (apply router/url handler params))

(defn switch-route
  "Reagent component that renders different components for different routes.

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
