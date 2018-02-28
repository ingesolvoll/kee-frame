(defproject kee-frame "0.0.3-SNAPSHOT"
  :description "A micro-framework on top of re-frame"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/ingesolvoll/kee-frame"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojurescript "1.9.946"]
                 [reagent "0.8.0-alpha2"]
                 [re-frame "0.10.3-alpha1" :exclusions [reagent]]
                 [bidi "2.0.16"]
                 [venantius/accountant "0.1.9"]
                 [org.clojure/core.match "0.3.0-alpha5"]]

  :deploy-repositories [["releases" {:sign-releases false
                                     :url           "https://clojars.org/repo"
                                     :username      :env/clojars_username
                                     :password      :env/clojars_password}]]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :source-paths ["src"])