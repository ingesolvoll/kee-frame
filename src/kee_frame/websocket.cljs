(ns kee-frame.websocket
  (:require
    [chord.client :as chord]
    [clojure.core.async :refer [<! >! chan close!]]
    [re-frame.core :as rf])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

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

(defn- start-websocket [{:keys [path dispatch wrap-message format]}]
  (let [{:keys [output-chan]} (socket-for path)]
    (go
      (let [url (websocket-url path)
            {:keys [ws-channel error]} (<! (chord/ws-ch url {:format (or format :transit-json)}))]
        (if error
          (swap! websockets assoc-in [path :state] :dead)
          (do
            (swap! websockets assoc-in [path :state] :alive)
            (swap! websockets assoc-in [path :socket] ws-channel)
            (send-messages! output-chan ws-channel wrap-message)
            (receive-messages! ws-channel dispatch)))))))

(defn socket-not-found [path websockets]
  (throw (ex-info (str "Could not find socket for path " path) {:available-sockets websockets})))

(defn close-socket [path]
  (when-let [socket (:ws-channel (socket-for path))]
    (close! socket)
    (socket-not-found path  @websockets)))

(defn ws-send! [_ [_ path message]]
  (let [{:keys [output-chan]} (socket-for path)]
    (if output-chan
      (go (>! output-chan message))
      (socket-not-found path  @websockets)))
  nil)

(rf/reg-fx ::open start-websocket)

(rf/reg-fx ::close close-socket)

(rf/reg-event-fx ::send ws-send!)