(ns kee-frame.api-test
  (:require [clojure.test :refer :all]
            [kee-frame.core :as kf]
            [re-frame.core :as rf]
            [day8.re-frame.test :as rf-test])
  (:import (clojure.lang ExceptionInfo)))

(rf/reg-fx
  :http-xhrio
  (fn [opts]
    (rf/dispatch (:on-success opts))))

(deftest using-but-not-using-chain
  (testing "Can explicitly dispatch to next id"
    (rf-test/run-test-sync
      (rf/reg-sub :test-prop :test-prop)
      (kf/reg-chain :test-chain
                    (fn [_ _] {:dispatch [:test-chain-1]})
                    (fn [_ _] {:db {:test-prop :dispatch-worked}}))
      (rf/dispatch [:test-chain])
      (is (= :dispatch-worked @(rf/subscribe [:test-prop])))))

  (testing "Can explicitly :http :on-success to next id"
    (rf-test/run-test-sync
      (rf/reg-sub :test-prop :test-prop)
      (kf/reg-chain :test-chain
                    (fn [_ _] {:http-xhrio {:on-success [:test-chain-1]}})
                    (fn [_ _] {:db {:test-prop :on-success-worked}}))
      (rf/dispatch [:test-chain])
      (is (= :on-success-worked @(rf/subscribe [:test-prop]))))))

(deftest error-cases
  (testing "Nothing left to fill in"
    (rf-test/run-test-sync
      (kf/reg-chain :test-chain
                    (fn [_ _] {:dispatch [:no-no]})
                    (fn [_ _] nil))
      (is (thrown? ExceptionInfo
                   (rf/dispatch [:test-chain]))))))