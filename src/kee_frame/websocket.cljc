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
    [kee-frame.interop :as interop]))

(defn- receive-messages! [ws-chan dispatch]
  (go-loop []
    (when-let [message (<! ws-chan)]
      (rf/dispatch [dispatch message])
      (recur))))

(defn- send-messages!
  [buffer-chan ws-chan wrap-message]
  (go-loop []
    (let [message (<! buffer-chan)]
      (>! ws-chan (wrap-message message))
      (recur))))

(defn- socket-for [db path]
  (or
    (get-in db [::sockets path])
    (let [buffer-chan (chan)
          ref {:buffer-chan buffer-chan}]
      (rf/dispatch [::created path buffer-chan])
      ref)))

(defn start-websocket [create-socket {:keys [path dispatch wrap-message format]}]
  (go
    (let [url (interop/websocket-url path)
          {:keys [ws-channel error]} (<! (create-socket url {:format format}))]
      (if error
        (rf/dispatch [::error path error])
        (rf/dispatch [::connected path ws-channel wrap-message dispatch])))))

(defn- socket-not-found [path websockets]
  (throw (ex-info (str "No socket registered for path " path) websockets)))

(k/reg-event-db
  ::created
  (fn [db [path buffer-chan]]
    (assoc-in db [::sockets path] {:buffer-chan buffer-chan
                                   :state       :initializing})))

(k/reg-event-db
  ::error
  (fn [db [path message]]
    (update-in db [::sockets path] merge {:state   :error
                                          :message message})))

(k/reg-event-fx
  ::connected
  (fn [{:keys [db]} [path ws-chan wrap-message dispatch]]
    (let [{:keys [buffer-chan]} (socket-for db path)]
      (send-messages! buffer-chan ws-chan wrap-message)
      (receive-messages! ws-chan dispatch)
      {:db (update-in db [::sockets path] merge {:ws-chan ws-chan
                                                 :state   :connected})})))

(rf/reg-fx ::open (partial start-websocket interop/create-socket))

(k/reg-event-fx ::close (fn [{:keys [db]} [path]]
                          (if-let [socket (:ws-channel (socket-for db path))]
                            (close! socket)
                            (socket-not-found path (::sockets db)))
                          nil))

(k/reg-event-fx ::send (fn [{:keys [db]} [path message]]
                         (if-let [buffer-chan (:buffer-chan (socket-for db path))]
                           (go (>! buffer-chan message))
                           (socket-not-found path (::sockets db)))
                         nil))

(rf/reg-sub ::state (fn [db [_ path]]
                       (get-in db [::sockets path])))