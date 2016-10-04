; nothing

(set-env! :dependencies '[[org.clojure/clojure "1.9.0-alpha13"]
                          [org.clojure/tools.reader "1.0.0-beta3"]
                          [com.taoensso/timbre "4.7.4"]
                          [com.cemerick/url "0.1.1"]
;                          [com.cemerick/friend "0.2.3"]
                          [org.clojure/core.async "0.2.391"]
                          [org.clojure/clojurescript "1.9.229"]
                          [adzerk/boot-cljs "1.7.228-1"]
                          [adzerk/boot-reload "0.4.12"]
                          [pandeiro/boot-http "0.7.3"]
                          [enlive "1.1.6"]
                          [hiccup "1.0.5"]
                          [http-kit "2.1.18"]
;                          [ring "1.5.0"]
                          [compojure "1.5.1"]
                          [ring/ring-defaults "0.2.1"]
                          [tolitius/boot-check "0.1.3"]
                          [com.novemberain/monger "3.1.0"]
                          [com.stuartsierra/component "0.3.1"]
                          [environ "1.1.0"]
                          [capacitor "0.6.0"]
                          [org.danielsz/system "0.3.2-SNAPSHOT"]
                          [clojurewerkz/quartzite "2.0.0"]
                          [boot-environ "1.1.0"]]
          :source-paths '#{"src/"}
          :resource-paths '#{"resources/"})

(task-options!
 pom {:project 'tully
      :version "0.0.1"}
 aot {:namespace #{'tully.main}
      :all true}
 jar {:main 'tully.main}
 target {:dir #{"target"}}
 (cljs 
  :optimizations :none) )

(require '[boot.repl]
         '[adzerk.boot-cljs :refer [cljs]]
         '[adzerk.boot-reload :refer [reload]]
         '[pandeiro.boot-http :refer [serve]]
         '[environ.boot :refer [environ]]
         '[tully.systems :refer [dev-system]]
         '[system.boot :refer [system run]]
         '[tolitius.boot-check :as check])

(deftask dev
  "Run a restartable system in the REPL"
  []
  (comp
   (environ :env {:mongo-host "127.0.0.1"
                  :mongo-port 27017
                  :mongo-db "monger-test"
                  :web-port 3001
                  :influx-host "127.0.0.1"
                  :influx-port 8086
                  :influx-db "tully-metrics"})
   ;; (serve :dir "target"
   ;;        :handler 'tully.handler/app
   ;;        :httpkit true)
   (watch :verbose true)
   (cljs 
    :optimizations :none)
;;   (target :dir #{"target"})
   (system :sys #'dev-system
           :auto true
           :files ["handler.clj" "systems.clj"])
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
  (comp (aot) (pom) (uber) (jar) (target)))

;; https://github.com/danielsz/system/tree/master/examples/boot for example

(swap! boot.repl/*default-dependencies*
       concat '[[cider/cider-nrepl "0.13.0"]
                [refactor-nrepl "2.0.0-SNAPSHOT"]])

(swap! boot.repl/*default-middleware*
       conj 'cider.nrepl/cider-middleware)

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
