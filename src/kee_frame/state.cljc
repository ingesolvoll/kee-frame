(ns kee-frame.state)

(def controllers (atom {}))

(def router (atom nil))

(def navigator (atom nil))

(def app-db-spec (atom nil))

(def debug? (atom false))
