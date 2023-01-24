(ns ^:no-doc kee-frame.scroll
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]
            [reagent.core :as r]
            [clerk.core :as clerk]))

(rf/reg-event-db ::connection-balance
                 (fn [db [_ inc-or-dec]]
                   (update db ::route-counter inc-or-dec)))

(defn start! []
  (clerk/initialize!))

(defn monitor-requests! [route]
  (clerk/navigate-page! (:path route))
  (swap! ajax/default-interceptors
         (fn [interceptors]
           (conj (filter #(not= "route-interceptor" (:name %)) interceptors)
                 (ajax/to-interceptor {:name     "route-interceptor"
                                       :request  (fn [request]
                                                   (rf/dispatch [::connection-balance inc])
                                                   request)
                                       :response (fn [response]
                                                   (rf/dispatch [::connection-balance dec])
                                                   response)})))))

(rf/reg-event-fx ::scroll
                 (fn [_ _]
                   (r/after-render clerk/after-render!)
                   nil))

(rf/reg-event-fx ::poll
                 (fn [{:keys [db]} [_ active-route counter]]
                   (let [route (:kee-frame/route db)
                         balance (::route-counter db)]
                     (when (= route active-route)
                       (cond
                         (not (pos? balance)) {:dispatch [::scroll]}
                         (pos? balance) {:dispatch-later [{:ms       50
                                                           :dispatch [::poll active-route (inc counter)]}]}
                         (< 20 counter) {:db (assoc db ::route-counter nil)})))))

