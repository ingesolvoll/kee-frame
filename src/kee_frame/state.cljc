(ns kee-frame.state)

(def controllers (atom {}))

(def router (atom nil))

(def navigator (atom nil))

(def app-db-spec (atom nil))

(def debug? (atom false))

(def links (atom [{:present?    (fn [effects] (:http-xhrio effects))
                   :dispatched? (fn [effects] (get-in effects [:http-xhrio :on-success]))
                   :insert      (fn [effects event] (assoc-in effects [:http-xhrio :on-success] event))}]))