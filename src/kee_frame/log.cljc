(ns kee-frame.log
  (:require [re-frame.core :as rf]
            [taoensso.timbre :as log]))

(def config
  (merge {:level      :debug
          :middleware [#(assoc % :raw-console? true)]}
         #?(:cljs
            {:appenders {:console (log/console-appender)}})))

(rf/reg-fx :log
  (fn [[level & vargs :as arg]]
    (when arg
      (log/log level vargs))))
