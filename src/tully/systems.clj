(ns tully.systems
  (:require [system.core :refer [defsystem]]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [taoensso.sente.server-adapters.http-kit
             :refer (sente-web-server-adapter)]
            [cognitect.transit :as transit]
            [taoensso.sente.packers.transit :as sente-transit]
            [clojure.core.async :refer [chan sliding-buffer]]
            [tully.influx :refer [new-influx-db]]
            [tully.metrics-requester :refer [new-metrics-requester]]
            [tully.handler :refer [main-routes secure-routes event-msg-handler*]]
            [tully.http-kit-server :refer [new-web-server]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            (system.components
             [quartzite :refer [new-scheduler]]
             [handler :refer [new-handler]]
             [sente :refer [new-channel-sockets]])
            [system.components.quartzite :refer [new-scheduler]]
            [system.components.handler :refer [new-handler]]
            [system.components.endpoint :refer [new-endpoint]]
            [system.components.middleware :refer [new-middleware]]
            [system.components.mongo :refer [new-mongo-db]]
            )
    (:import [org.bson.types ObjectId]))

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

(def objectid-writer
  (transit/write-handler
   "object-id"
   (fn [o] (-> ^ObjectId o str))
   (fn [o] (-> ^ObjectId o str))))

(def objectid-reader
  (transit/read-handler
   (fn [ostring] (ObjectId. ostring))))

(defsystem dev-system
  [;; base infrastructure components
   :store (new-mongo-db (env :mongo-host) (env :mongo-port) (env :mongo-db) {})
   ;; disabled for now for port conflict
   :influx (new-influx-db (env :influx-host) (env :influx-port) (env :influx-db))
   ;;   :scheduler (new-scheduler)
   ;; disabled for now for restart nullpointerexception
   :middleware (new-middleware {:middleware [[wrap-defaults :defaults]]
                                :defaults site-defaults})

   ;;channels
   :metrics-request-chan (chan 10)   ;increase this or use sliding-buffer?

   ;; dependent components
   :routes (component/using
            (new-endpoint secure-routes)
            [:store])
   :handler (component/using
             (new-handler)
             [:routes :middleware])
   :web-server (component/using
                (new-web-server (Integer. (env :web-port)))
                [:handler])
   :sente (new-channel-sockets event-msg-handler* sente-web-server-adapter
                               {:packer (sente-transit/get-transit-packer
                                         :json
                                         {:handlers {ObjectId objectid-writer}}
                                         {:handlers {"object-id" objectid-reader}}
                                         )
                                :user-id-fn (fn [ring-req] (:client-id ring-req))})
                               
   :metrics-requester (component/using
                       (new-metrics-requester)
                       {:influx-component :influx
                        :request-chan :metrics-request-chan})
   ])
