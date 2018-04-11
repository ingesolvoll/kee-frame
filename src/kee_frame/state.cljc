(ns kee-frame.state)

(def controllers (atom {}))

(def router (atom nil))

(def navigator (atom nil))

(def app-db-spec (atom nil))

(def debug? (atom false))

(def default-links [{:effect-present? (fn [effects] (:http-xhrio effects))
                     :get-dispatch    (fn [effects] (get-in effects [:http-xhrio :on-success]))
                     :set-dispatch    (fn [effects dispatch] (assoc-in effects [:http-xhrio :on-success] dispatch))}])
(def links (atom default-links))

(defn reset-state! []
  (reset! controllers {})
  (reset! links default-links)
  (reset! router nil)
  (reset! navigator nil))