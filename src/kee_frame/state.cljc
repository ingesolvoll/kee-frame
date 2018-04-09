(ns kee-frame.state)

(def controllers (atom {}))

(def router (atom nil))

(def navigator (atom nil))

(def app-db-spec (atom nil))

(def debug? (atom false))

(def links (atom [{:effect-present?   (fn [effects] (:http-xhrio effects))
                   :explicit-dispatch (fn [effects] (get-in effects [:http-xhrio :on-success]))
                   :insert-dispatch   (fn [effects dispatch] (assoc-in effects [:http-xhrio :on-success] dispatch))}]))

(defn reset-state! []
  (reset! controllers {})
  (reset! router nil)
  (reset! navigator nil))