(defproject kee-frame "1.1.2-SNAPSHOT"
  :description "A micro-framework on top of re-frame"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/ingesolvoll/kee-frame"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.773"]
                 [re-frame "1.1.1"]
                 [re-chain "1.2"]
                 [metosin/reitit-core "0.5.6"]
                 [day8.re-frame/http-fx "v0.2.0"]
                 [cljs-ajax "0.8.0"]
                 [clj-statecharts "0.0.1-SNAPSHOT"]
                 [com.taoensso/timbre "5.1.0"]
                 [venantius/accountant "0.2.5"]
                 [org.clojure/core.match "1.0.0"]
                 [expound "0.8.6"]
                 [day8.re-frame/test "0.1.5"]
                 [breaking-point "0.1.2"]
                 [pez/clerk "1.0.0"]]

  :plugins [[com.jakemccrary/lein-test-refresh "0.24.1"]]
  :deploy-repositories [["clojars" {:sign-releases false
                                    :url           "https://clojars.org/repo"
                                    :username      :env/clojars_username
                                    :password      :env/clojars_password}]]

  :source-paths ["src" "assets"]
  :aliases {"deploy!" ["do" ["test"] ["deploy" "clojars"]]})
