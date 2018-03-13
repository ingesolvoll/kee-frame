(ns kee-frame.controller
  (:require
    [re-frame.core :as rf]
    #?(:cljs
       [cljs.core.match :refer [match]])
    #?(:clj
    [clojure.core.match :refer [match]])
    [kee-frame.state :as state]
    [kee-frame.spec :as spec]
    [clojure.spec.alpha :as s]))

(defn process-params [params route]
  (cond
    (vector? params) (get-in route params)
    (ifn? params) (params route)))

(defn validate-and-dispatch! [dispatch]
  (when dispatch
    (when-not (s/valid? ::spec/event-vector dispatch)
      (throw (ex-info "Invalid dispatch value"
                      (s/explain-data ::spec/event-vector dispatch))))
    (rf/dispatch dispatch)))

(defmacro wrap-arity-exception [body message data]
  `(try
     ~body
     (catch clojure.lang.ArityException _#
       (throw (ex-info ~message ~data)))))

(defn do-start [id ctx start params]
  (when start
    (when @state/debug?
      (rf/console :log "Starting controller " id " with params " params))
    (cond
      (vector? start) (rf/dispatch (conj start params))
      (ifn? start) (validate-and-dispatch!
                     (wrap-arity-exception
                       (start ctx params)
                       "Wrong arity for start function. Should be 2 arguments, ctx and params"
                       {:id     id
                        :ctx    ctx
                        :params params})))))

(defn do-stop [id ctx stop]
  (when stop
    (rf/console :log "Stopping controller " id)
    (cond
      (vector? stop) (rf/dispatch stop)
      (ifn? stop) (validate-and-dispatch!
                    (wrap-arity-exception
                      (stop ctx)
                      "Wrong arity for stop function. Should be 1 arguments, the re-frame ctx"
                      {:id     id
                       :ctx    ctx})))))

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
              [id (assoc controller :last-params (process-controller id controller ctx route))]))
       (into {})))