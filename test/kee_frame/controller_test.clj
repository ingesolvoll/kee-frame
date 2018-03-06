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
      (-> controllers
          (c/apply-route {} {:handler :some-page})
          (c/apply-route {} {:handler :other-page}))
      (is (= [[:start/event true]
              [:stop/event]] @events)))))