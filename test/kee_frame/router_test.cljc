(ns kee-frame.router-test
  (:require [clojure.test :refer [deftest testing is]]
            [kee-frame.router :as router]
            [kee-frame.api :as api]
            [reitit.core :as reitit]))

(defn router [routes hash?] (router/->ReititRouter (reitit/router routes) hash? nil))

(deftest can-produce-hash-urls
  (let [r (router [["/" :root]
                   ["/item/:id" :item]] true)]
    (testing "Root"
      (is (= "/#/" (api/data->url r [:root]))))

    (testing "Item"
      (is (= "/#/item/1" (api/data->url r [:item {:id 1}]))))

    (testing "Item with missing id throws"
      (is (thrown?
           #?(:clj clojure.lang.ExceptionInfo
              :cljs js/Error)
           (api/data->url r [:item]))))))

(deftest can-parse-hash-urls
  (let [r (router [["/" :root]
                   ["/item/:id" :item]] true)]

    (testing "Root"
      (is (= :root (-> (api/url->data r "/")
                       :data
                       :name))))

    (testing "Item with path params and query string"
      (let [{:keys [data path-params query-string]} (api/url->data r "/item/1?query=string")]
        (is (= "query=string" query-string))
        (is (= :item (:name data)))
        (is (= "1" (:id path-params)))))))
