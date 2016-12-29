(ns tully.main
  (:gen-class)
  (:require [system.repl :refer [set-init! start]]
            [tully.systems :refer [dev-system]]))

(defn -main
  "Start a dev system"
  [& args]
  (let [system (or (first args) #'dev-system)]
    (set-init! system)
    (start)))
