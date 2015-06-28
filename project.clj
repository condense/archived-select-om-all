(defproject condense/select-om-all "0.1.1"
  :description "Flexible select-style component for use in Om apps"
  :url "https://github.com/condense/select-om-all"

  :dependencies [[org.clojure/clojure "1.7.0-RC2"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.omcljs/om "0.8.8"]
                 [sablono "0.3.4"]
                 [cljsjs/fixed-data-table "0.3.0-0"]
                 [figwheel "0.3.5" :scope "test"] ]

  :plugins [[lein-cljsbuild "1.0.6"]
            [lein-figwheel "0.3.5"]]

  :source-paths ["src" "target/classes"]

  :clean-targets ["out" "out/select-om-all.js"]

  :cljsbuild {:builds [{:id           "basics"
                        :source-paths ["src" "examples/basics/src"]
                        :figwheel     true
                        :compiler     {:output-to     "examples/basics/main.js"
                                       :output-dir    "examples/basics/out"
                                       :source-map    true
                                       :optimizations :none}}]})
