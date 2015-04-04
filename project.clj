(defproject condense/select-om-all "0.1.0-SNAPSHOT"
  :description "Flexible select-style component for use in Om apps"
  :url "https://github.com/condense/select-om-all"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3165"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.omcljs/om "0.8.8"]
                 [sablono "0.3.4"]
                 [cljsjs/fixed-data-table "0.1.2-1"]
                 [figwheel "0.2.5" :scope "test"]
                 [precursor/om-i "0.1.7" :scope "test"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-figwheel "0.2.5"]]

  :source-paths ["src" "target/classes"]

  :clean-targets ["out" "out/select-om-all.js"]

  :cljsbuild {:builds [{:id           "basics"
                        :source-paths ["src" "examples/basics/src"]
                        :compiler     {
                                       :output-to     "examples/basics/main.js"
                                       :output-dir    "examples/basics/out"
                                       :source-map    true
                                       :optimizations :none}}]})
