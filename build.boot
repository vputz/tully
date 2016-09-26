; nothing

(set-env! :dependencies '[[org.clojure/clojure "1.8.0"]
                          [org.clojure/tools.reader "1.0.0-beta3"]
                          [org.clojure/tools.logging "0.3.1"]
                          [com.cemerick/url "0.1.1"]
                          [com.cemerick/friend "0.2.3"]
                          [enlive "1.1.6"]
                          [http-kit "2.1.18"]
                          [compojure "1.5.1"]
                          [tolitius/boot-check "0.1.3"]
                          [com.novemberain/monger "3.1.0"]
                          [com.stuartsierra/component "0.3.1"]
                          [environ "1.1.0"]
                          [capacitor "0.6.0"]
                          [org.danielsz/system "0.3.2-SNAPSHOT"]
                          [boot-environ "1.1.0"]]
          :source-paths '#{"src/"})

(task-options!
 pom {:project 'tully
      :version "0.0.1"}
 aot {:namespace #{'tully.main}
      :all true}
 jar {:main 'tully.main}
 target {:dir #{"out"}}
 )

(require '[boot.repl]
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
                  :web-port 3000
                  :influx-host "127.0.0.1"
                  :influx-port 8086
                  :influx-db "tully-metrics"})
   (watch :verbose true)
   (system :sys #'dev-system :auto true)
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
       concat '[[cider/cider-nrepl "0.10.0"]
                [refactor-nrepl "2.0.0-SNAPSHOT"]])

(swap! boot.repl/*default-middleware*
       conj 'cider.nrepl/cider-middleware)

(deftask cider "CIDER profile"
  []
  (require 'boot.repl)
  (swap! @(resolve 'boot.repl/*default-dependencies*)
         concat '[[org.clojure/tools.nrepl "0.2.12"]
                  [cider/cider-nrepl "0.10.0"]
                  [refactor-nrepl "2.0.0-SNAPSHOT"]])
  (swap! @(resolve 'boot.repl/*default-middleware*)
         concat '[cider.nrepl/cider-middleware
                  refactor-nrepl.middleware/wrap-refactor])
  identity
  )
