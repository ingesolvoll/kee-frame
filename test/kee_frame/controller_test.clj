(ns kee-frame.controller-test
  (:require [clojure.test :refer :all]
            [kee-frame.controller :as c]))

(def controllers {:my-controller {:params (constantly true)
                                  :start  [:start/event]
                                  :stop   [:stop/event]}})

(deftest hey
  (is (true? (-> (c/apply-route controllers {} {:handler :some-page})
                 :my-controller
                 :last-params))))