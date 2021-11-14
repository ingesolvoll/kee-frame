(ns kee-frame.fsm.beta
  (:require [re-frame.core :as f]))

(defmacro with-fsm [fsm & body]
  (let [id (:id fsm)]
    `(reagent.core/with-let [_# (f/dispatch [::start ~fsm])]
       ~@body
       ~(list
         'finally
         `(re-frame.core/dispatch [::stop ~id])))))

