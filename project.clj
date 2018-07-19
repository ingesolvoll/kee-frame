(defproject kee-frame "0.2.6"
  :description "A micro-framework on top of re-frame"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/ingesolvoll/kee-frame"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.312"]
                 [re-frame "0.10.5"]
                 [bidi "2.1.3"]
                 [day8.re-frame/http-fx "0.1.6"]
                 [cljs-ajax "0.7.3"]
                 [org.clojure/core.async "0.3.442"]
                 [jarohen/chord "0.8.1"]
                 [venantius/accountant "0.2.4"]
                 [org.clojure/core.match "0.3.0-alpha5"]
                 [expound "0.7.0"]
                 [day8.re-frame/test "0.1.5"]
                 [breaking-point "0.1.2"]
                 [metosin/reitit "0.1.3"]
                 #_[sandbags/aido "0.3.5"]]

  :plugins [[com.jakemccrary/lein-test-refresh "0.20.0"]
            [venantius/ultra "0.5.2"]]
  :deploy-repositories [["clojars" {:sign-releases false
                                    :url           "https://clojars.org/repo"
                                    :username      :env/clojars_username
                                    :password      :env/clojars_password}]]

  :source-paths ["src" "assets"]
  :aliases {"deploy!" ["do" ["test"] ["deploy" "clojars"]]})