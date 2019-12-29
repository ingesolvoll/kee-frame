(ns kee-frame.error
  (:require [reagent.core :as r]))

(defn default-error-body [[err info]]
  (js/console.log "An error occurred: " err)
  [:pre [:code (pr-str info)]])

(defn boundary
  ([body] [boundary default-error-body body])
  ([_ _]
   (let [err-state (r/atom nil)]
     (r/create-class
      {:display-name        "ErrBoundary"
       :component-did-catch (fn [err info]
                              (reset! err-state [err info]))
       :reagent-render      (fn [error-body body]
                              (assert (fn? error-body) "Error body comp must be a function")
                              (if (nil? @err-state)
                                body
                                [error-body @err-state]))}))))