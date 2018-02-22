(ns kee-frame.core
  (:require [kee-frame.state :as state]
            [re-frame.core :as rf]
            [kee-frame.controller :as controller]))


(defn start! [routes]
  (controller/start-router! routes))

(rf/reg-sub :route (fn [db] (:route db)))

(defn reg-controller [id controller]
  (swap! state/controllers assoc id controller))

(defn reg-view [id component]
  (swap! state/components assoc id component))

(defn dispatch-view [id]
  (let [route (rf/subscribe [:route])
        component (get @state/components id)]
    (fn [_]
      (if-not component
        [:div "Missing view for dispatch point " id]
        [component @route]))))