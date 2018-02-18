(defproject kee-frame "0.1.0"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojurescript "1.9.946"]
                 [reagent "0.8.0-alpha2"]
                 [re-frame "0.10.3-alpha1" :exclusions [reagent]]
                 [bidi "2.0.16"]
                 [venantius/accountant "0.1.9"]
                 [org.clojure/core.match "0.3.0-alpha5"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js/compiled"]

  :deploy-repositories [["releases"  {:sign-releases false :url "https://clojars.org/repo"}
                         ["snapshots" {:sign-releases false :url "https://clojars.org/repo"}]

                         :release-tasks [["vcs" "assert-committed"]
                                         ["change" "version" "leiningen.release/bump-version" "release"]
                                         ["vcs" "commit"]
                                         ["vcs" "tag" "v" "--no-sign"]
                                         ["deploy"]
                                         ["change" "version" "leiningen.release/bump-version"]
                                         ["vcs" "commit"]
                                         ["vcs" "push"]]]]


  :cljsbuild {:builds [{:id           "app"
                        :source-paths ["src"]
                        :figwheel     true
                        :compiler     {:main                 cybersea.core
                                       :asset-path           "/js/compiled/out"
                                       :output-to            "resources/public/js/compiled/app.js"
                                       :output-dir           "resources/public/js/compiled/out"
                                       :source-map-timestamp true
                                       :parallel-build       true
                                       :compiler-stats       true
                                       :foreign-libs         [{:file     "resources/public/js/jquery.flowchart.min.js"
                                                               :requires ["cljsjs.jquery-ui"]
                                                               :provides ["jquery.flowchart"]}
                                                              {:file     "resources/public/js/ace.min.inc.js"
                                                               :provides ["cljsjs.ace"]}
                                                              {:file     "resources/public/js/jstree.min.inc.js"
                                                               :provides ["cljsjs.jstree"]}]
                                       :closure-defines      {cybersea.event-util/debug-events      true
                                                              "re_frame.trace.trace_enabled_QMARK_" true}
                                       :preloads             [devtools.preload day8.re-frame.trace.preload]
                                       :external-config      {:devtools/config {:features-to-install [:formatters]}}}}
                       {:id           "min"
                        :source-paths ["src/cljs" "src/cljc"]
                        :compiler     {:output-to      "resources/public/js/compiled/app.js"
                                       :optimizations  :advanced
                                       :foreign-libs   [{:file     "resources/public/js/jquery.flowchart.min.js"
                                                         :requires ["cljsjs.jquery-ui"]
                                                         :provides ["jquery.flowchart"]}
                                                        {:file     "resources/public/js/ace.min.inc.js"
                                                         :provides ["cljsjs.ace"]}
                                                        {:file     "resources/public/js/jstree.min.inc.js"
                                                         :provides ["cljsjs.jstree"]}]
                                       :infer-externs  true
                                       :externs        ["externs/ace.ext.js" "externs/highcharts.ext.js" "externs/jstree.ext.js" "externs/promesa.ext.js" "externs/codemirror.ext.js"]
                                       :parallel-build true}}]})
