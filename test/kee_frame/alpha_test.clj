(ns kee-frame.alpha-test
  (:require [clojure.test :refer :all]
            [kee-frame.alpha :as m]
            [kee-frame.core :as k]))

(deftest haha
  (let [f (m/==>apply assoc :mordi :fardi)]
    (is (= {:mordi :fardi} (f {} [1 2 3])))))