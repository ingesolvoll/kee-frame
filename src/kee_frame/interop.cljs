(ns ^:no-doc kee-frame.interop
  (:require [kee-frame.api :as api]
            [accountant.core :as accountant]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [day8.re-frame.http-fx]
            [chord.client :as chord]
            [breaking-point.core :as bp]
            [clojure.string :as str]))

(defrecord AccountantNavigator []
  api/Navigator
  (dispatch-current! [_]
    (accountant/dispatch-current!))
  (navigate! [_ url]
    (accountant/navigate! url)))

(def create-socket chord/ws-ch)

(defn websocket-url [path]
  (if (str/starts-with? path "/")
    (str (if (= "https:" (-> js/document .-location .-protocol))
           "wss://"
           "ws://")
         (-> js/document .-location .-host)
         path)
    ;; Consider this an url for now.
    path))

(defn make-navigator
  [opts]
  (accountant/configure-navigation! opts)
  (->AccountantNavigator))

(defn render-root [root-component]
  (when root-component
    (if-let [app-element (.getElementById js/document "app")]
      (reagent/render root-component
                      app-element)
      (throw (ex-info "Could not find element with id 'app' to mount app into" {:component root-component})))))

(defn breakpoints-or-defaults [breakpoints]
  (or breakpoints
      {:debounce-ms 166
       :breakpoints [:mobile
                     768
                     :tablet
                     992
                     :small-monitor
                     1200
                     :large-monitor]}))

(defn set-breakpoint-subs [breakpoints]
  (bp/register-subs (:breakpoints (breakpoints-or-defaults breakpoints))))

(defn set-breakpoints [breakpoints]
  (rf/dispatch-sync [::bp/set-breakpoints (breakpoints-or-defaults breakpoints)]))