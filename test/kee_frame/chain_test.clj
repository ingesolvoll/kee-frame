(ns kee-frame.chain-test
  (:require [clojure.test :refer :all]
            [kee-frame.chain :as chain]
            [clojure.string :as str])
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
    (let [handler (eval (chain/make-fx-event {:data `{:db [[:property [:kee-frame.core/params 0]]
                                                           [:property-transformed [:kee-frame.core/params 0 str/capitalize]]]}}))
          effect (handler {:db {}} [:event "badaboom"])]
      (is (= {:db {:property             "badaboom"
                   :property-transformed "Badaboom"}}
             effect))))

  (testing "HTTP without next in chain throws"
    (is (thrown? ExceptionInfo
                 (chain/make-fx-event {:data {:http-xhrio {:method :get
                                                           :uri    "site.com"}}}))))
  (testing "Automatically inserted next on HTTP"
    (let [handler (eval (chain/make-fx-event {:next-id :next/step
                                              :data    {:http-xhrio {:method :get
                                                                     :uri    "site.com"}}}))
          effect (handler {:db {}} [:some-event])]
      (is (= [:next/step] (-> effect :http-xhrio :on-success)))))

  (testing "Automatically inserted next on dispatch"
    (let [handler (eval (chain/make-fx-event {:next-id :next/step
                                              :data    {:db {:something "something"}}}))
          effect (handler {:db {}} [:some-event])]
      (is (= [:next/step] (-> effect :dispatch))))))