(ns kee-frame.navigation-test
  (:require [clojure.test :refer [deftest testing is]]
            [kee-frame.core :as k]
            [re-frame.core :as rf]
            [day8.re-frame.test :as rf-test]
            [kee-frame.state :as state]))

(def routes [["/" :index]
             ["/testing/:id" :some-route]])

(deftest can-navigate-through-events
  (testing "Good navigation"
    (state/reset-state!)
    (k/reg-controller :test-controller {:params #(when (= (-> % :data :name) :some-route)
                                                   (:path-params %))
                                        :start  [::test-event-2]})
    (rf-test/run-test-sync
     (rf/reg-sub :test-prop (fn [db _] (:test-prop db)))
     (rf/reg-event-fx ::test-event
                      (fn [_ _] {:navigate-to [:some-route {:id 1}]}))
     (rf/reg-event-fx ::test-event-2
                      (fn [_ [_ args]] {:db {:test-prop args}}))
     (k/start! {:routes routes})
     (rf/dispatch [::test-event])
     (is (= {:id "1"} @(rf/subscribe [:test-prop])))))

  (testing "Bad dispatch value returned form start fn throws exception"
    (state/reset-state!)
    (k/reg-controller :test-controller {:params (constantly true)
                                        :start  (fn [_ _] "This is not cool")})
    (rf-test/run-test-sync
     (is (thrown-with-msg?
          #?(:clj  clojure.lang.ExceptionInfo
             :cljs js/Error)
          #"Invalid dispatch value"
          (k/start! {:routes routes}))))))

(deftest routing-syntax
  (testing "Regression for changed router api"
    (state/reset-state!)
    (k/start! {:routes routes})
    (is (thrown?
         #?(:clj  clojure.lang.ExceptionInfo
            :cljs js/Error)
         (k/path-for :some-route)))))
