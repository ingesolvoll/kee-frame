(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def lib 'kee-frame/kee-frame)
;; if you want a version of MAJOR.MINOR.COMMITS:
(def version (format "1.2.%s" (b/git-count-revs nil)))

(defn install [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/jar)
      (bb/install)))

(defn ci [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/run-tests)
      (bb/jar)
      (bb/deploy)))
