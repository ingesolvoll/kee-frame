(ns kee-frame.api)

(defprotocol Navigator
  (dispatch-current! [_])
  (navigate! [_ url]))

(defprotocol Router
  (data->url [_ data])
  (url->data [_ url]))