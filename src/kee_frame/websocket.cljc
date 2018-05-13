(ns kee-frame.websocket
  (:require
    [clojure.core.async :refer [go go-loop <! >! chan close!]]
    [re-frame.core :as rf]))

(defonce ^:private websockets (atom {}))

(defn receive-messages! [websocket-channel dispatch]
  (go-loop []
    (when-let [message (<! websocket-channel)]
      (rf/dispatch [dispatch message])
      (recur))))

(defn- send-messages!
  "Receives messages from output-channel and send them to the server"
  [output-channel websocket-channel wrap-message]
  (go-loop []
    (let [message (<! output-channel)]
      (>! websocket-channel (wrap-message message))
      (recur))))

(defn- websocket-url [path]
  (str (if (= "https:" (-> js/document .-location .-protocol))
         "wss://"
         "ws://")
       (-> js/document .-location .-host)
       path))

(defn socket-for [path]
  (or
    (get @websockets path)
    (let [output-chan (chan)
          ref {:output-chan output-chan
               :state       :initializing}]
      (swap! websockets assoc path ref)
      ref)))

(defn- start-websocket [create-socket {:keys [path dispatch wrap-message format]}]
  (let [{:keys [output-chan]} (socket-for path)]
    (go
      (let [url (websocket-url path)
            {:keys [ws-channel error]} (<! (create-socket url {:format format}))]
        (if error
          (swap! websockets assoc path {:state   :error
                                        :message error})
          (do
            (swap! websockets assoc path {:state  :alive
                                          :socket ws-channel})
            (send-messages! output-chan ws-channel wrap-message)
            (receive-messages! ws-channel dispatch)))))))

(defn socket-not-found [path websockets]
  (throw (ex-info (str "Could not find socket for path " path) {:available-sockets websockets})))

(defn close-socket [path]
  (when-let [socket (:ws-channel (socket-for path))]
    (close! socket)
    (socket-not-found path @websockets)))

(defn ws-send! [_ [_ path message]]
  (let [{:keys [output-chan]} (socket-for path)]
    (if output-chan
      (go (>! output-chan message))
      (socket-not-found path @websockets)))
  nil)