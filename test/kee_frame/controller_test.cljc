(ns kee-frame.controller-test
  (:require [clojure.test :refer [deftest testing is]]
            [kee-frame.controller :as c]))

(deftest compact-syntax
  (testing "Can start and stop"
    (let [controllers      {:my-controller {:params #(-> % :handler (= :some-page) (or nil))
                                            :start  [:start/event]
                                            :stop   [:stop/event]}}
          {:keys [:update-controllers :dispatch-n]} (c/controller-effects controllers {} {:handler :some-page})
          next-controllers (c/update-controllers controllers update-controllers)
          actions-2        (c/controller-effects next-controllers {} {:handler :other-page})]
      (is (= [[:start/event true]] dispatch-n))
      (is (= [[:stop/event]] (:dispatch-n actions-2)))))



  (testing "Will trigger only once when always on"
    (let [controllers      {:always-on-controller {:params (constantly true)
                                                   :start  [:start/event]}}
          {:keys [:update-controllers :dispatch-n]} (c/controller-effects controllers {} {:handler :some-page})
          next-controllers (c/update-controllers controllers update-controllers)
          actions-2        (c/controller-effects next-controllers {} {:handler :other-page})]
      (is (= [[:start/event true]] dispatch-n))
      (is (= {:update-controllers [] :dispatch-n nil} actions-2)))))

(deftest fn-syntax
  (testing "Can start and stop"
    (is (= [[:start/event]]
           (:dispatch-n
            (c/controller-effects
             {:my-controller {:params (constantly true)
                              :start  (fn [ctx params]
                                        [:start/event])}}
             {}
             {:handler :some-page}))))))

(deftest invalid-start-return
  (is (thrown-with-msg?
       #?(:clj  clojure.lang.ExceptionInfo
          :cljs js/Error)
       #"Invalid dispatch value"
       (->> (c/controller-effects {:invalid-controller {:params (constantly true)
                                                        :start  (fn [ctx params]
                                                                  "heisann")}}
                                  {}
                                  {:handler :some-page})
            :dispatch-n
            doall))))