(ns kee-frame.chain-test
  (:require [clojure.test :refer :all]
            [kee-frame.chain :as chain])
  (:import (clojure.lang ExceptionInfo)))

(deftest can-translate-instruction-to-event-function
  (let [[do-fn [log-fn] [reg-fn]] (chain/make-step {:id   :event-id
                                                    :type :db
                                                    :data [[:path :to 0]]})]
    (is (= 'do do-fn))
    (is (= 're-frame.core/console log-fn))
    (is (= 're-frame.core/reg-event-db reg-fn))))

(deftest fx-event
  (testing "DB as side effect in FX handler"
    (let [handler (eval (chain/make-fx-event {:data {:db [[:property :x]
                                                          [:property2 [:kee-frame.core/params 0]]]}}))
          effect (handler {:db {}} [:event "badaboom"])]
      (is (= effect {:db {:property  :x
                          :property2 "badaboom"}}))))

  (testing "HTTP without next in chain throws"
    (is (thrown? ExceptionInfo
                 (chain/make-fx-event {:data {:http-xhrio {:method :get
                                                           :uri    "site.com"}}})))))