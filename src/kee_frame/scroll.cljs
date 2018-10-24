(ns ^:no-doc kee-frame.scroll
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]
            [reagent.core :as r]))

(rf/reg-event-db ::connection-balance
                 (fn [db [_ route inc-or-dec]]
                   (assoc-in db [:route-counter] {:route route :balance (inc-or-dec (get-in db [:route-counter :balance]))})))

(defn monitor-requests! [route]
  ;; TODO: Clerk navigate here
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

(rf/reg-event-fx ::restore-scroll
                 (fn [_ _]
                   ;; TODO Clerk after render here.
                   (r/after-render nil)
                   nil))

(rf/reg-event-fx ::poll
                 (fn [{:keys [db]} [_ active-route counter]]
                   (let [{:keys [route balance]} (:route-counter db)]
                     (when (= route active-route)
                       (cond
                         (not (pos? balance)) {:dispatch [::restore-scroll]}
                         (pos? balance) {:dispatch-later [{:ms       100
                                                           :dispatch [:poll-scroll active-route (inc counter)]}]}
                         (< 10 counter) {:db (assoc db :route-counter nil)})))))

