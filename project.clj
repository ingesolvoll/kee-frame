(defproject kee-frame "0.1.0-SNAPSHOT"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojurescript "1.9.946"]
                 [reagent "0.8.0-alpha2"]
                 [re-frame "0.10.3-alpha1" :exclusions [reagent]]
                 [bidi "2.0.16"]
                 [venantius/accountant "0.1.9"]
                 [org.clojure/core.match "0.3.0-alpha5"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js/compiled"]

  :deploy-repositories [["releases" {:sign-releases false :url "https://clojars.org/repo"}
                         ["snapshots" {:sign-releases false :url "https://clojars.org/repo"}]]]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]


  :cljsbuild {:builds [{:id           "min"
                        :source-paths ["src"]
                        :compiler     {:output-to      "resources/public/js/compiled/app.js"
                                       :optimizations  :advanced
                                       :parallel-build true}}]})