(ns kee-frame.interop
  (:require [kee-frame.api :as api]
            [accountant.core :as accountant]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [chord.client :as chord]
            [breaking-point.core :as bp]))

(defrecord AccountantNavigator []
  api/Navigator
  (dispatch-current! [_]
    (accountant/dispatch-current!))
  (navigate! [_ url]
    (accountant/navigate! url)))

(def create-socket chord/ws-ch)

(defn websocket-url [path]
  (str (if (= "https:" (-> js/document .-location .-protocol))
         "wss://"
         "ws://")
       (-> js/document .-location .-host)
       path))

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
                   (assoc db :kee-frame.core/window-dimensions dimensions)))

(rf/reg-sub :kee-frame.core/window-dimensions :kee-frame.core/window-dimensions)

(rf/reg-sub :kee-frame.core/window-size (fn [db] :small))   ;;TODO

(defn set-breakpoints [breakpoints]
  (rf/dispatch-sync [::bp/set-breakpoints
                     {:breakpoints (or breakpoints
                                       [:mobile
                                        768
                                        :tablet
                                        992
                                        :small-monitor
                                        1200
                                        :large-monitor])
                      :debounce-ms 166}]))