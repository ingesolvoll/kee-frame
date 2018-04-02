(ns kee-frame.state)

(def controllers (atom {}))

(def routes (atom nil))

(def router (atom nil))

(def app-db-spec (atom nil))

(def debug? (atom false))
