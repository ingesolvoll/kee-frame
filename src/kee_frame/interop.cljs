(ns kee-frame.interop
  (:require [kee-frame.api :as api]
            [accountant.core :as accountant]
            [reagent.core :as reagent]))

(defn accountant-router [opts]
  (accountant/configure-navigation! opts)
  (reify api/Navigator
    (dispatch-current! [_]
      (accountant/dispatch-current!))
    (navigate! [_ url]
      (accountant/navigate! url))))

(defn make-navigator
  [opts]
  (accountant-router opts))

(defn render-root [root-component]
  (when root-component
    (if-let [app-element (.getElementById js/document "app")]
      (reagent/render root-component
                      app-element)
      (throw (ex-info "Could not find element with id 'app' to mount app into" {:component root-component})))))