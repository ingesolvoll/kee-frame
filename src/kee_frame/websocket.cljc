(ns kee-frame.websocket
  #?(:cljs
     (:require-macros
       [cljs.core.async.macros :refer [go go-loop]]))
  (:require
    [clojure.core.async :refer [<! >! chan close!]]
    #?(:clj
    [clojure.core.async :refer [go go-loop]])
    [re-frame.core :as rf]
    [kee-frame.core :as k]
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

(defn- socket-for [db path]
  (or
    (get-in db [::sockets path])
    (let [output-chan (chan)
          ref {:output-chan output-chan}]
      (rf/dispatch [::created path output-chan])
      ref)))

(defn start-websocket [create-socket {:keys [path dispatch wrap-message format]}]
  (go
    (let [url (interop/websocket-url path)
          {:keys [ws-channel error]} (<! (create-socket url {:format format}))]
      (if error
        (rf/dispatch [::error path error])
        (do
          (rf/dispatch [::connected path ws-channel])
          (send-messages! (chan) ws-channel wrap-message)
          (receive-messages! ws-channel dispatch))))))

(defn- socket-not-found [path websockets]
  (throw (ex-info (str "Could not find socket for path " path) {:available-sockets websockets})))

(k/reg-event-db
  ::created
  (fn [db [path output-chan]]
    (assoc-in db [::sockets path] {:output-chan output-chan
                                   :state       :initializing})))

(k/reg-event-db
  ::error
  (fn [db [path message]]
    (update-in db [::sockets path] merge {:state   :error
                                          :message message})))

(k/reg-event-db
  ::connected
  (fn [db [path ws-chan]]
    (assoc-in db [::sockets path] {:ws-chan ws-chan
                                   :state   :connected})))

(rf/reg-fx ::open (partial start-websocket interop/create-socket))

(k/reg-event-fx ::close (fn [{:keys [db]} [path]]
                          (if-let [socket (:ws-channel (socket-for db path))]
                            (close! socket)
                            (socket-not-found path @state/websockets))
                          nil))

(k/reg-event-fx ::send (fn [{:keys [db]} [path message]]
                         (if-let [socket (:output-chan (socket-for db path))]
                           (go (>! socket message))
                           (socket-not-found path @state/websockets))
                         nil))

(rf/reg-sub ::sub (fn [db [_ path]]
                    (get-in db [::sockets path])))