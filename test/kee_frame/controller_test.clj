(ns kee-frame.controller-test
  (:require [clojure.test :refer :all]
            [kee-frame.controller :as c]
            [re-frame.core :as rf])
  (:import (clojure.lang ExceptionInfo)))

(deftest compact-syntax
  (testing "Can start and stop"
    (let [events (atom [])]
      (with-redefs [rf/dispatch #(swap! events conj %)]
        (-> {:my-controller {:params #(-> % :handler (= :some-page) (or nil))
                             :start  [:start/event]
                             :stop   [:stop/event]}}
            (c/apply-route {} {:handler :some-page})
            (c/apply-route {} {:handler :other-page}))
        (is (= [[:start/event true]
                [:stop/event]] @events)))))

  (testing "Will trigger only once when always on"
    (let [events (atom [])]
      (with-redefs [rf/dispatch #(swap! events conj %)]
        (-> {:always-on-controller {:params (constantly true)
                                    :start  [:start/event]}}
            (c/apply-route {} {:handler :some-page})
            (c/apply-route {} {:handler :other-page})
            (c/apply-route {} {:handler :third-page}))
        (is (= [[:start/event true]] @events))))))

(deftest fn-syntax
  (testing "Can start and stop"
    (let [events (atom [])]
      (with-redefs [rf/dispatch #(swap! events conj %)]
        (-> {:my-controller {:params (constantly true)
                             :start  (fn [ctx params]
                                       [:start/event])}}
            (c/apply-route {} {:handler :some-page}))
        (is (= [[:start/event]] @events)))))

  (testing "Error handling"
    (is (thrown-with-msg? ExceptionInfo #"Invalid dispatch value"
                          (c/apply-route {:my-controller {:params (constantly true)
                                                          :start  (fn [ctx params]
                                                                    "heisann")}}
                                         {}
                                         {:handler :some-page}))))
  (testing "Error handling on arity start"
    (is (thrown-with-msg? ExceptionInfo #"Wrong arity for start function"
                          (c/apply-route {:my-controller {:params (constantly true)
                                                          :start  (fn [ctx]
                                                                    [:does "not" "matter"])}}
                                         {}
                                         {:handler :some-page}))))

  (testing "Error handling on arity for stop"
    (is (thrown-with-msg? ExceptionInfo #"Wrong arity for stop function"
                          (-> {:my-controller {:params (fn [{:keys [handler]}] (= :some-page handler))
                                               :start  (fn [ctx p] nil)
                                               :stop   (fn [] [:nah])}}
                              (c/apply-route {} {:handler :some-page})
                              (c/apply-route {} {:handler :not-some-page}))))))

