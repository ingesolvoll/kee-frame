(ns kee-frame.chain-test
  (:require [clojure.test :refer :all]
            [kee-frame.chain :as chain]
            [day8.re-frame.test :as rf-test]
            [kee-frame.state :as state]
            [kee-frame.core :as k]
            [re-frame.core :as rf]
            [re-frame.interceptor :refer [->interceptor get-effect get-coeffect assoc-coeffect assoc-effect]])
  (:import (clojure.lang ExceptionInfo)))

(def insert-marker
  (->interceptor
    :id :insert-marker
    :after (fn [context]
             (assoc-in context [:effects :db :marker] 69))))

(deftest utils
  (testing "Can produce next step id with namespaced keyword"
    (is (= :ns/id-2 (chain/step-id :ns/id 2))))
  (testing "Can produce next step id with plain keyword"
    (is (= :keyw-2 (chain/step-id :keyw 2)))))

(deftest interceptors
  (testing "Inserts dispatch to next"
    (is (= {:dispatch [:next]}
           (chain/link-effects :next [] {}))))

  (testing "Throws when only one potential link and it's taken"
    (is (thrown? ExceptionInfo
                 (chain/link-effects :next [] {:dispatch [:something-bad]}))))

  (testing "Inserts on-success on http"
    (is (= [:next]
           (-> (chain/link-effects :next [] {:http-xhrio {}})
               (get-in [:http-xhrio :on-success])))))

  (testing "Can use special pointer to next action when explicit params are needed"
    (is (= {:dispatch [:next-event :a :b :c]}
           (chain/replace-pointers :next-event {:dispatch [:kee-frame.core/next :a :b :c]}))))

  (testing "Reports error when on-success or dispatch are specified and none of them point to correct next event"
    (is (thrown? ExceptionInfo
                 (chain/link-effects :next-event [] {:dispatch   [:something]
                                                     :http-xhrio {:on-success [:something-else]}}))))
  (testing "Exactly one event dispatches to next in chain."
    (is (= {:dispatch   [:break-out-of-here]
            :http-xhrio {:get        "cnn.com"
                         :on-success [:next-event]}}
           (chain/link-effects :next-event [] {:dispatch   [:break-out-of-here]
                                               :http-xhrio {:get "cnn.com"}}))))

  (testing "Will pass its parameters on to next in chain"
    (is (= {:dispatch [:next-event 1 2]}
           (-> {:coeffects {:event [:this-event 1 2]}}
               ((chain/effect-postprocessor :next-event))
               :effects)))

    (is (= {:dispatch [:next-event 1 2 3 4]}
           (-> {:coeffects {:event [:previous-event 1 2]}
                :effects   {:dispatch [:kee-frame.core/next 3 4]}}
               ((chain/effect-postprocessor :next-event))
               :effects)))))

(deftest outer-api
  (testing "Plain chain"
    (let [instructions (chain/collect-event-instructions :my/chain
                                                         [identity
                                                          identity])]
      (is (= [:my/chain :my/chain-1]
             (map :id instructions)))))

  (testing "Bad chain"
    (is (thrown-with-msg? ExceptionInfo #""
                          (chain/collect-event-instructions :my/chain
                                                            ["string-should-not-be-here"]))))

  (testing "Chain with interceptors"
    (is (= :debug (-> (chain/collect-event-instructions :my/chain
                                                        [[rf/debug]
                                                         identity])
                      first
                      :interceptors
                      first
                      :id))))

  (testing "Named chain"
    (let [instructions (chain/collect-named-event-instructions
                         [:step-1
                          identity
                          :step-2
                          identity])]
      (is (= [:step-1 :step-2]
             (map :id instructions)))))

  (testing "Bad named chain gives good error message"
    (is (thrown-with-msg? ExceptionInfo #""
                          (chain/collect-named-event-instructions
                            [:step-1
                             identity
                             :step-2])))))

(def custom-chain-links [{:effect-present? (fn [effects] (:my-custom-effect effects))
                          :get-dispatch    (fn [effects] (get-in effects [:my-custom-effect :got-it]))
                          :set-dispatch    (fn [effects dispatch] (assoc-in effects [:my-custom-effect :got-it] dispatch))}])

(def routes ["" {"/"               :index
                 ["/testing/" :id] :some-route}])

(deftest integration
  (testing "Custom chain links"
    (state/reset-state!)

    (rf-test/run-test-sync
      (k/start! {:routes      routes
                 :chain-links custom-chain-links})
      (rf/reg-fx :my-custom-effect (fn [config] (rf/dispatch (:got-it config))))
      (rf/reg-sub :test-prop :test-prop)
      (k/reg-chain :test-event
                   (fn [_ _] {:my-custom-effect {}})
                   (fn [_ _] {:db {:test-prop 2}}))
      (rf/dispatch [:test-event])
      (is (= 2 @(rf/subscribe [:test-prop])))))

  (testing "Chain with interceptor"
    (state/reset-state!)

    (rf-test/run-test-sync
      (k/start! {:routes      routes})
      (rf/reg-sub :marker :marker)
      (k/reg-chain :test-event
                   (fn [_ _] {})
                   [insert-marker]
                   (fn [_ _] nil))
      (rf/dispatch [:test-event])
      (is (= 69 @(rf/subscribe [:marker]))))))