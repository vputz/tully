(ns tully.main
  (:require [environ.core :refer [env]]
            [system.repl :refer [set-init! start]]
            [tully.systems :refer [dev-system]]
            [tully.db :as db]
            [com.stuartsierra.component :as component]
            )
  (:gen-class))

(defn -main
  "Start a dev system"
  [& args]
  (let [system (or (first args) #'dev-system)]
    (set-init! system)
    (start)))
