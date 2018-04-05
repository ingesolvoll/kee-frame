(defproject kee-frame "0.2.0-SNAPSHOT"
  :description "A micro-framework on top of re-frame"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/ingesolvoll/kee-frame"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.946"]
                 [re-frame "0.10.5"]
                 [bidi "2.0.16"]
                 [venantius/accountant "0.2.4"]
                 [org.clojure/core.match "0.3.0-alpha5"]
                 [expound "0.5.0"]
                 [day8.re-frame/test "0.1.5"]]

  :plugins [[com.jakemccrary/lein-test-refresh "0.20.0"]
            [venantius/ultra "0.5.2"]]
  :deploy-repositories [["clojars" {:sign-releases false
                                    :url           "https://clojars.org/repo"
                                    :username      :env/clojars_username
                                    :password      :env/clojars_password}]]

  :source-paths ["src"]
  :aliases {"deploy!" ["do" ["test"] ["deploy" "clojars"]]})