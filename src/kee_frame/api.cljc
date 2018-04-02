(ns kee-frame.api)

(defprotocol Router
  (dispatch-current! [_])
  (navigate! [_ url]))