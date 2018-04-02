(ns kee-frame.interop
  (:require [kee-frame.api :as api]))

(defrecord TestRouter [url nav-handler path-exists?]
  api/Router
  (dispatch-current! [{:keys [url]}]
    (nav-handler url))
  (navigate! [this url]
    (when (path-exists? url)
      (nav-handler url))
    (assoc this :url url)))

(defn make-router
  [opts]
  (map->TestRouter (assoc opts :url "/")))

(defn render-root [root-component]
  (throw (ex-info "JVM can't handle " {:root root-component})))