(ns kee-frame.controller-test
  (:require [clojure.test :refer :all]
            [kee-frame.controller :as c]
            [re-frame.core :as rf]))

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
        (is (= [[:start/even true]] @events))))))