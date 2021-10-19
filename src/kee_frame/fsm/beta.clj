(ns kee-frame.fsm.beta
  (:require [clojure.spec.alpha :as s]
            [re-frame.core :as f]))

(s/def ::binding (s/and vector?
                        (s/cat :fsm-symbol symbol? :fsm any?)))

(defmacro with-fsm [binding & body]
  (let [parsed (s/conform ::binding binding)]
    (when (= ::s/invalid parsed)
      (throw (ex-info "with-fsm accepts exactly one binding pair, the symbol and the value containing the fsm."
                      (s/explain-data ::binding binding))))
    (let [{:keys [:fsm-symbol :fsm]} parsed]
      `(reagent.core/with-let [~fsm-symbol ~fsm
                               _# (f/dispatch [::start ~fsm-symbol])]
         ~@body
         ~(list
           'finally
           `(re-frame.core/dispatch [::stop (:id ~fsm-symbol)]))))))

