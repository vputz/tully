(ns tully.systems
  (:require [system.core :refer [defsystem]]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [tully.influx :refer [new-influx-db]]
            [system.components.mongo :refer [new-mongo-db]]
            [system.components.http-kit :refer [new-web-server]]))

(defrecord Test [msg]
  component/Lifecycle
  (start [component]
    (do 
      (log/info "Starting " msg)
      component))

  (stop [component]
    (do
      (log/info "Stopping " msg)
      component)))

(defn new-test [msg]
  (map->Test {:msg msg}))

(defsystem dev-system
  [:test (new-test "lobster")
   :store (new-mongo-db (env :mongo-host) (env :mongo-port) (env :mongo-db) {})
   :web-server (new-web-server (env :web-port) )
   :influx (new-influx-db (env :influx-host) (env :influx-port) (env :influx-db))])
