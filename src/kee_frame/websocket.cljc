(ns kee-frame.websocket
  (:require
    [clojure.core.async :refer [go go-loop <! >! chan close!]]
    [re-frame.core :as rf]
    [kee-frame.interop :as interop]
    [kee-frame.state :as state]))

(defn- receive-messages! [websocket-channel dispatch]
  (go-loop []
    (when-let [message (<! websocket-channel)]
      (rf/dispatch [dispatch message])
      (recur))))

(defn- send-messages!
  [output-channel websocket-channel wrap-message]
  (go-loop []
    (let [message (<! output-channel)]
      (>! websocket-channel (wrap-message message))
      (recur))))

(defn- socket-for [path]
  (or
    (get @state/websockets path)
    (let [output-chan (chan)
          ref {:output-chan output-chan
               :state       :initializing}]
      (swap! state/websockets assoc path ref)
      ref)))

(defn start-websocket [create-socket {:keys [path dispatch wrap-message format]}]
  (let [{:keys [output-chan]} (socket-for path)]
    (go
      (let [url (interop/websocket-url path)
            {:keys [ws-channel error]} (<! (create-socket url {:format format}))]
        (if error
          (swap! state/websockets assoc path {:state   :error
                                              :message error})
          (do
            (swap! state/websockets assoc path {:state  :alive
                                                :socket ws-channel})
            (send-messages! output-chan ws-channel wrap-message)
            (receive-messages! ws-channel dispatch)))))))

(defn- socket-not-found [path websockets]
  (throw (ex-info (str "Could not find socket for path " path) {:available-sockets websockets})))

(defn close-socket [path]
  (if-let [socket (:ws-channel (socket-for path))]
    (close! socket)
    (socket-not-found path @state/websockets)))

(defn ws-send! [path message]
  (if-let [socket (:ws-channel (socket-for path))]
    (go (>! socket message))
    (socket-not-found path @state/websockets)))