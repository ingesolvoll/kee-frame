(ns kee-frame.interop
  (:require [kee-frame.api :as api]))

(defrecord TestNavigator [nav-handler path-exists?]
  api/Navigator
  (dispatch-current! [{:keys [url]}]
    (nav-handler url))
  (navigate! [this url]
    (when (path-exists? url)
      (nav-handler url))
    (assoc this :url url)))

(defn make-navigator
  [opts]
  (map->TestNavigator (assoc opts :url "/")))

(defn render-root [root-component]
  (when root-component
    (throw (ex-info "JVM can't render to the DOM" {:root root-component}))))