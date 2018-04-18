(ns kee-frame.interop
  (:require [kee-frame.api :as api]
            [accountant.core :as accountant]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [kee-frame.core :as k]))

(defrecord AccountantNavigator []
  api/Navigator
  (dispatch-current! [_]
    (accountant/dispatch-current!))
  (navigate! [_ url]
    (accountant/navigate! url)))

(defn accountant-router [opts]
  (accountant/configure-navigation! opts)
  (->AccountantNavigator))

(defn make-navigator
  [opts]
  (accountant-router opts))

(defn render-root [root-component]
  (when root-component
    (if-let [app-element (.getElementById js/document "app")]
      (reagent/render root-component
                      app-element)
      (throw (ex-info "Could not find element with id 'app' to mount app into" {:component root-component})))))

(rf/reg-event-db ::set-window-dimensions
                 (fn [db [_ dimensions]]
                   (assoc db ::k/window-dimensions dimensions)))

(rf/reg-sub ::k/window-dimensions ::k/window-dimensions)

(rf/reg-sub ::k/window-size (fn [db] :small))               ;;TODO

(defn responsive-setup []
  (.addEventListener js/window
                     "resize"
                     #(rf/dispatch [::set-window-dimensions {:width  js/window.innerWidth
                                                             :height js/window.innerHeight}])))