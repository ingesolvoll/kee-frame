(ns kee-frame.alpha-test
  (:require [clojure.test :refer :all]
            [kee-frame.alpha :as m]))

(deftest haha
  (let [f (m/fn-assoc :mordi [:kee-frame.core/params 1])]
    (is (= {:mordi 2} (f {} [1 2])))))