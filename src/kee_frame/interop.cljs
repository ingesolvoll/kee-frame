(ns ^:no-doc kee-frame.interop
  (:require [kee-frame.api :as api]
            [accountant.core :as accountant]
            [reagent.dom :as reagent-dom]
            [re-frame.core :as rf]
            [day8.re-frame.http-fx]
            [lambdaisland.glogi :as log :refer-macros [debug]]
            [lambdaisland.glogi.console :as glogi-console]
            [breaking-point.core :as bp]
            [re-frame.loggers :as rf.log]))

(defrecord AccountantNavigator []
  api/Navigator
  (dispatch-current! [_]
    (accountant/dispatch-current!))
  (navigate! [_ url]
    (accountant/navigate! url)))

(defn make-navigator
  [opts]
  (accountant/configure-navigation! opts)
  (->AccountantNavigator))

(defn render-root [root-component]
  (when root-component
    (if-let [app-element (.getElementById js/document "app")]

      (reagent-dom/render root-component
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

(defn set-log-level! [{:keys [overwrites?]
                       :or   {overwrites? false}}]
  (when-not overwrites?
    (rf.log/set-loggers!
     {:warn (fn [& args]
              (when-not (re-find #"^re-frame: overwriting" (first args))
                (apply js/console.warn args)))})))

(defn set-timeout [f ms]
  (js/setTimeout f ms))

(defn clear-timeout [t]
  (js/clearTimeout t))

(glogi-console/install!)

(defn set-log-levels [config]
  (log/set-levels
   {:glogi/root :debug}))

(set-log-level! nil)

(defn log [level key message]
  (case level
    :debug (log/debug key message)
    :info (log/info key message)))

(rf/reg-fx :log
  (fn [[level key message]]
    (log level key message)))