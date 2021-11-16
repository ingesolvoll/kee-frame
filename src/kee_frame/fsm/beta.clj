(ns kee-frame.fsm.beta
  (:require [clojure.spec.alpha :as s]
            [re-frame.core :as f]))

(s/def ::binding (s/and vector?
                        (s/cat :subscription-symbol symbol? :fsm any?)))

(defmacro with-fsm [binding & body]
  (let [parsed (s/conform ::binding binding)]
    (when (= ::s/invalid parsed)
      (throw (ex-info "with-fsm accepts exactly one binding pair, the subscription symbol and the FSM declaration."
                      (s/explain-data ::binding binding))))
    (let [{:keys [:subscription-symbol :fsm]} parsed]
      `(reagent.core/with-let [~subscription-symbol (f/subscribe [::state (:id ~fsm)])
                               _# (f/dispatch [::start ~fsm])]
         ~@body
         ~(list
           'finally
           `(re-frame.core/dispatch [::stop (:id ~fsm)]))))))

