(ns kee-frame.controller-test
  (:require [clojure.test :refer :all]
            [kee-frame.controller :as c]
            [re-frame.core :as rf]))

(def controllers {:my-controller {:params #(-> % :handler (= :some-page) (or nil))
                                  :start  [:start/event]
                                  :stop   [:stop/event]}})

(deftest hey
  (let [events (atom [])]
    (with-redefs [rf/dispatch #(swap! events conj %)]
      (let [controllers-after-first-route (c/apply-route controllers {} {:handler :some-page})]
        (is (= [[:start/event true]] @events))
        (c/apply-route controllers-after-first-route {} {:handler :other-page})
        (is (= [[:start/event true]
                [:stop/event]] @events))))))