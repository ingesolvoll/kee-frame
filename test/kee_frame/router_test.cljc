(ns kee-frame.router-test
  (:require [clojure.test :refer [deftest testing is]]
            [kee-frame.router :as router]
            [kee-frame.api :as api]
            [reitit.core :as reitit]))

(defn router [routes hash? base-path] (router/->ReititRouter (reitit/router routes) hash? base-path nil))

(defn produce-urls-helper [hash? base-path]
  (let [r (router [["/" :root]
                   ["/item/:id" :item]] hash? base-path)
        hash-path (if hash? "/#" "")]
    (testing "Root"
      (is (= (str base-path hash-path "/") (api/data->url r [:root]))))

    (testing "Item"
      (is (= (str base-path hash-path "/item/1") (api/data->url r [:item {:id 1}]))))

    (testing "Item with missing id throws"
      (is (thrown?
           #?(:clj clojure.lang.ExceptionInfo
              :cljs js/Error)
           (api/data->url r [:item]))))))

(defn parse-urls-helper [hash? base-path]
  (let [r (router [["/" :root]
                   ["/item/:id" :item]] true base-path)
        hash-path (if hash? "/#" "")]

    (testing "Root"
      (is (= :root (-> (api/url->data r (str base-path hash-path "/"))
                       :data
                       :name))))

    (testing "Item with path params and query string"
      (let [{:keys [data path-params query-string]} 
            (api/url->data r (str base-path hash-path "/item/1?query=string"))]
        (is (= "query=string" query-string))
        (is (= :item (:name data)))
        (is (= "1" (:id path-params)))))))

(deftest can-produce-urls
  (doseq [hash?      [true false]
          base-path  ["" "/prefix"]]
    (produce-urls-helper hash? base-path)))

(deftest can-parse-urls
  (doseq [hash?      [true false]
          base-path  ["" "/prefix"]]
    (parse-urls-helper hash? base-path)))
