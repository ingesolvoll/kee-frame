(ns ^:no-doc kee-frame.scroll
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]
            [reagent.core :as r]
            [clerk.core :as clerk]))

(rf/reg-event-db ::connection-balance
                 (fn [db [_ route inc-or-dec]]
                   (-> db
                       (assoc-in [:route-counter :route] route)
                       (update-in  [:route-counter :balance] inc-or-dec))))

(defn start! []
  (clerk/initialize!))

(defn monitor-requests! [route]
  (clerk/navigate-page! (:path route))
  (swap! ajax/default-interceptors
         (fn [interceptors]
           (conj (filter #(not= "route-interceptor" (:name %)) interceptors)
                 (ajax/to-interceptor {:name     "route-interceptor"
                                       :request  (fn [request]
                                                   (rf/dispatch [::connection-balance route inc])
                                                   request)
                                       :response (fn [response]
                                                   (rf/dispatch [::connection-balance route dec])
                                                   response)})))))

(rf/reg-event-fx ::scroll
                 (fn [_ _]
                   (r/after-render clerk/after-render!)
                   nil))

(rf/reg-event-fx ::poll
                 (fn [{:keys [db]} [_ active-route counter]]
                   (let [{:keys [route balance]} (:route-counter db)]
                     (when (= route active-route)
                       (cond
                         (not (pos? balance)) {:dispatch [::scroll]}
                         (pos? balance) {:dispatch-later [{:ms       50
                                                           :dispatch [::poll active-route (inc counter)]}]}
                         (< 20 counter) {:db (assoc db :route-counter nil)})))))

