(ns kee-frame.alpha
  (:require [re-frame.core :as rf]
            [clojure.walk :as walk]))

(defn is? [x k]
  (and (vector? x)
       (= k (first x))))

(defn param? [x]
  (is? x :kee-frame.core/params))

(defn ctx? [x]
  (is? x :kee-frame.core/ctx))

(defn db? [x]
  (is? x :kee-frame.core/db))

(defn walk-params [params data]
  (walk/postwalk
    (fn [x]
      (if (param? x)
        (let [[_ index] x]
          `(nth ~params ~index))
        x))
    data))

(defn walk-placeholders [ctx params data]
  (->> data
       (walk/postwalk
         (fn [x]
           (cond (ctx? x) (let [path (vec (next x))]
                            `(get-in ~ctx ~path))
                 (db? x) (let [path (vec (next x))]
                           `(get-in (:db ~ctx) ~path))
                 :pass-through x)))
       (walk-params params)))


(defmacro fn-fx [& body]
  (let [ctx (gensym "ctx")
        params (gensym "params")]
    `(fn [~ctx ~params] ~@(walk-placeholders ctx params body))))

(defmacro fn-apply [f & args]
  (let [db (gensym "db")
        params (gensym "params")]
    `(fn [~db ~params] (apply ~f ~db ~@(walk-params params args)))))

(defmacro fn-assoc [k v]
  (let [db (gensym "db")
        params (gensym "params")]
    `(fn [~db ~params] (assoc ~db k ~(walk-params params v)))))