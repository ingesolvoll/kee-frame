(ns kee-frame.log
  (:require [re-frame.core :as rf]
            [re-frame.loggers :as rfl]
            [taoensso.timbre :as log]))

(def config
  (merge {:level      :info
          :middleware [#(assoc % :raw-console? true)]}
         #?(:cljs
            {:appenders {:console (log/console-appender)}})))

(defn init! [user-config]
  (log/set-config! (merge config user-config)))

(rf/reg-fx :log
  (fn [[level & vargs :as arg]]
    (when arg
      (log/log level vargs))))

#?(:cljs
   (rfl/set-loggers!
    {:warn (fn [& args]
             (when-not (re-find #"^re-frame: overwriting" (first args))
               (apply js/console.warn args)))}))