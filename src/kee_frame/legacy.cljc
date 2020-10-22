(ns kee-frame.legacy
  (:require [kee-frame.event-logger :as event-logger]
            [re-chain.core :as chain]
            [re-frame.core :as rf :refer [console]]))

;; Interceptors used by all chains and events registered through kee-frame
(def kee-frame-interceptors [event-logger/interceptor rf/trim-v])

(defn- reg-warn [id]
  ;; This warning will kick in somewhere along 1.x
  ;;(console :warn (str id  " kee-frame.core/reg-* have been deprecated. Use the corresponding re-frame and re-chain ones instead, and apply the global interceptors you need through kee-frame.core/start! instead."))
  )

(defn reg-event-fx
  "Exactly same signature as `re-frame.core/reg-event-fx`. Use this version if you want kee-frame logging and spec validation.

  `re-frame.core/trim-v` interceptor is also applied."
  ([id handler] (reg-event-fx id nil handler))
  ([id interceptors handler]
   (reg-warn id)
   (rf/reg-event-fx id (concat kee-frame-interceptors interceptors) handler)))

(defn reg-event-db
  "Exactly same signature as `re-frame.core/reg-event-db`. Use this version if you want kee-frame logging and spec validation.

  `re-frame.core/trim-v` interceptor is also applied."
  ([id handler] (reg-event-db id nil handler))
  ([id interceptors handler]
   (reg-warn id)
   (rf/reg-event-db id (concat kee-frame-interceptors interceptors) handler)))

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
  (reg-warn id)
  (apply chain/reg-chain* id kee-frame-interceptors handlers))

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
  (reg-warn "")
  (apply chain/reg-chain-named* kee-frame-interceptors handlers))