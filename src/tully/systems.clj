(ns tully.systems
  (:require [system.core :refer [defsystem]]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :refer [chan sliding-buffer]]
            [tully.influx :refer [new-influx-db]]
            [tully.metrics-requester :refer [new-metrics-requester]]
            [tully.handler :refer [app]]
            [tully.http-kit-server :refer [new-web-server]]
            [system.components.quartzite :refer [new-scheduler]]
            [system.components.mongo :refer [new-mongo-db]]
))

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
  [;; base infrastructure components
   :store (new-mongo-db (env :mongo-host) (env :mongo-port) (env :mongo-db) {})
   ;; disabled for now for port conflict
   :influx (new-influx-db (env :influx-host) (env :influx-port) (env :influx-db))
   ;;   :scheduler (new-scheduler)
   ;; disabled for now for restart nullpointerexception
   :web-server (new-web-server (env :web-port) app)

   ;;channels
   :metrics-request-chan (chan 10)   ;increase this or use sliding-buffer?

   ;; dependent components
   :metrics-requester (component/using
                       (new-metrics-requester)
                       {:influx-component :influx
                        :request-chan :metrics-request-chan})
   ])
