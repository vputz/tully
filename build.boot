                                        ; nothing

(set-env! :dependencies '[[org.clojure/clojure "1.9.0-alpha13"]
                          [org.clojure/tools.reader "1.0.0-beta3"]
                          [clj-time "0.8.0"]
                          [cljsjs/d3 "4.3.0-2"]
                          [riemann-clojure-client "0.4.4"]
                          [de.active-group/timbre-riemann "0.2.0"]
                          [com.taoensso/timbre "4.8.0"]
                          [com.taoensso/sente "1.11.0"]
                          [com.taoensso/encore "2.88.1"]
                          [com.cognitect/transit-cljs "0.8.239"]
                          [com.cognitect/transit-clj "0.8.290"]
                          [com.cemerick/url "0.1.1"]
                          [ring "1.5.0"]
                          [devcards "0.2.2"]
                          [org.clojure/clojurescript "1.9.229"]
                          [reagent "0.6.0"]
                          [re-frame "0.8.0"]
                          [com.cemerick/friend "0.2.3"]
                          [org.clojure/core.async "0.2.391"]
                          [adzerk/boot-cljs "1.7.228-1"]
                          [adzerk/boot-reload "0.4.12"]
                          [adzerk/boot-test "1.1.2" :scope "test"]
                          [pandeiro/boot-http "0.7.3"]
                          [enlive "1.1.6"]
                          [hiccup "1.0.5"]
                          [http-kit "2.1.18"]
                          [compojure "1.5.1"]
                          [ring/ring-defaults "0.2.1"]
                          [tolitius/boot-check "0.1.4"]
                          [com.novemberain/monger "3.1.0"]
                          [com.stuartsierra/component "0.3.1"]
                          [environ "1.1.0"]
                          [capacitor "0.6.0"]
                          [org.danielsz/system "0.3.2-SNAPSHOT"]
                          [clojurewerkz/quartzite "2.0.0"]
                          [webica "3.0.0-beta2-clj0" :scope "test"]
                          [kibu/pushy "0.3.6"]
                          [bidi "2.0.16"]
                          [boot-environ "1.1.0"]]
          :source-paths '#{"src/"}
          :resource-paths '#{"resources/"})

(task-options!
 pom {:project 'tully
      :version "0.0.1"}
 aot {:namespace #{'tully.main}
      :all true}
 jar {:main 'tully.main
      :file "tully.jar"}
 web {:serve 'tully.systems/serve-req}
 war {;:main 'tully.main
                                        ;:manifest {"Description" "citation metrics tracker"}
      :file "tully.war"}
 target {:dir #{"target"}}
 (cljs 
  :optimizations :none) )

(require '[boot.repl]
         '[adzerk.boot-cljs :refer [cljs]]
         '[adzerk.boot-reload :refer [reload]]
         '[adzerk.boot-test :refer [test]]
;         '[pandeiro.boot-http :refer [serve]]
         '[environ.boot :refer [environ]]
         '[tully.systems :refer [dev-system]]
         '[system.boot :refer [system run]]
         '[tolitius.boot-check :as check])

()

(deftask test-env
  "Set the testing environment"
  []
  (set-env! :source-paths '#{"src/" "test/"})
  (environ :env {:chromedriver-path "c:\\tools\\selenium\\chromedriver.exe"
                 :tully-mongo-host "127.0.0.1"
                 :tully-mongo-port "27017"
                 :tully-mongo-db "monger-test"
                 :tully-mongo-user "tully"
                 :tully-mongo-pass "tully"
                 :tully-web-port "3001"
                 :tully-influx-host "127.0.0.1"
                 :tully-influx-port "8086"
                 :tully-influx-user "tully"
                 :tully-influx-pass "tully"
                 :tully-influx-db "tullymetrics"
                 :tully-log-stdout-level "debug"
                 :tully-log-file-path "c:\\vputz\\clojure\\tully.log"
                 :tully-log-file-level "debug"
                 :tully-log-riemann-host "127.0.0.1"
                 :tully-log-riemann-port "5555"
                 :tully-log-riemann-level "info"
                 :tully-recent-interval "20"
                 :tully-interval-between-requests "2"})  )

(deftask unit-tests
  "Run basic unit tests"
  []
  (comp
   (test-env)
   (test :namespaces '[tully.core-test])))

(deftask uberwar
  []
  (comp (aot) (pom) (web) (uber) (war) (target)))

(deftask browser-tests
  "Run selenium browser tests"
  []
  (comp
   (test-env)
   (test :namespaces '[tully.browser-test])))

(deftask dev
  "Run a restartable system in the REPL"
  []
  (comp
   (test-env)   
   (watch :verbose true)
   (speak)
   (cljs 
    :optimizations :none)
   ;;   (target :dir #{"target"})
   (system :sys #'dev-system
           :auto true
           :files ["handler.clj" "systems.clj" "db.clj"])
   (repl :server true)))

(deftask dev-run
  "Run a dev system from the command line"
  []
  (comp
   (environ :env {})
   (run :main-namespace "tully.main" :arguments [#'dev-system])
   (wait)))

(deftask check-sources []
  "check sources with several checkers"
  (set-env! :source-paths #{"src"})
  (comp
   (check/with-kibit)))

(deftask uberjar []
  "build an uberjar"
  (comp (aot) (pom) (speak) (cljs 
                     :optimizations :advanced) (uber) (jar) (target)))

;; https://github.com/danielsz/system/tree/master/examples/boot for example

(swap! boot.repl/*default-dependencies*
       concat '[[cider/cider-nrepl "0.14.0"]
                [refactor-nrepl "2.3.0-SNAPSHOT"]])

(swap! boot.repl/*default-middleware*
       concat '[cider.nrepl/cider-middleware
                refactor-nrepl.middleware/wrap-refactor])

(deftask cider "CIDER profile"
  []
  (require 'boot.repl)
  (swap! @(resolve 'boot.repl/*default-dependencies*)
         concat '[[org.clojure/tools.nrepl "0.2.12"]
                  [cider/cider-nrepl "0.13.0"]
                  [refactor-nrepl "2.0.0-SNAPSHOT"]])
  (swap! @(resolve 'boot.repl/*default-middleware*)
         concat '[cider.nrepl/cider-middleware
                  refactor-nrepl.middleware/wrap-refactor])
  identity
  )
