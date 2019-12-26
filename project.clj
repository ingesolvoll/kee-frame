(defproject kee-frame "0.4.0-SNAPSHOT"
  :description "A micro-framework on top of re-frame"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/ingesolvoll/kee-frame"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.597"]
                 [re-frame "0.10.6"]
                 [re-chain "1.0"]
                 [metosin/reitit-core "0.2.13"]
                 [day8.re-frame/http-fx "0.1.6"]
                 [cljs-ajax "0.8.0"]
                 [venantius/accountant "0.2.4"]
                 [org.clojure/core.match "0.3.0-alpha5"]
                 [expound "0.8.2"]
                 [day8.re-frame/test "0.1.5"]
                 [breaking-point "0.1.2"]
                 [pez/clerk "1.0.0"]]

  :plugins [[com.jakemccrary/lein-test-refresh "0.20.0"]]
  :deploy-repositories [["clojars" {:sign-releases false
                                    :url           "https://clojars.org/repo"
                                    :username      :env/clojars_username
                                    :password      :env/clojars_password}]]

  :source-paths ["src" "assets"]
  :aliases {"deploy!" ["do" ["test"] ["deploy" "clojars"]]})
