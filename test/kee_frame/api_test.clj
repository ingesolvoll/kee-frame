(ns kee-frame.api-test
  (:require [clojure.test :refer :all]
            [kee-frame.core :as kf]
            [re-frame.core :as rf]
            [day8.re-frame.test :as rf-test]
            [re-chain.core :as chain]
            [kee-frame.router :as router])
  (:import (clojure.lang ExceptionInfo)))

(rf/reg-fx
  :http-xhrio
  (fn [opts]
    (rf/dispatch (:on-success opts))))

(deftest using-but-not-using-chain

  (chain/configure! router/default-chain-links)

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
                    (fn [_ []] {:http-xhrio {:on-success [:test-chain-1 4 5]}})
                    (fn [_ [_ _ three _ five]] {:db {:test-prop [three five]}}))
      (rf/dispatch [:test-chain 1 2 3])
      (is (= [3 5] @(rf/subscribe [:test-prop]))))))

(deftest base-cases
  (chain/configure! router/default-chain-links)
  (testing "using :next and http"
    (binding [chain/*replace-pointers* true]
      (rf-test/run-test-sync
       (rf/reg-sub :test-prop :test-prop)
       (kf/reg-chain :test-chain
                     (fn [_ [one two]] {:dispatch [:chain/next one two]})
                     (fn [_ _] {:http-xhrio {:uri "vg.no"}})
                     (fn [_ [& one-two-twice]] {:db {:test-prop one-two-twice}}))
       (rf/dispatch [:test-chain 1 2])
       (is (= [1 2 1 2] @(rf/subscribe [:test-prop])))))))

(deftest error-cases
  (testing "Nothing left to fill in"
    (rf-test/run-test-sync
      (kf/reg-chain :test-chain
                    (fn [_ _] {:dispatch [:no-no]})
                    (fn [_ _] nil))
      (is (thrown? ExceptionInfo
                   (rf/dispatch [:test-chain]))))))