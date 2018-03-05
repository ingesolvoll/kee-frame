(ns kee-frame.chain-test
  (:require [clojure.test :refer :all]
            [kee-frame.chain :as chain]))

(deftest can-translate-instruction-to-event-function
  (let [[do-fn [log-fn] [reg-fn]] (chain/make-step {:id   :event-id
                                                    :type :db
                                                    :data [[:path :to 0]]})]
    (is (= 'do do-fn))
    (is (= 're-frame.core/console log-fn))
    (is (= 're-frame.core/reg-event-db reg-fn))))