(ns tully.systems
  (:require [clojure
             [edn :as edn]
             [string :as str]]
            [clojure.core.async :refer [chan]]
            [cognitect.transit :as transit]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [system.components
             [endpoint :refer [new-endpoint]]
             [handler :refer [new-handler]]
             [http-kit :refer [new-web-server]]
             [middleware :refer [new-middleware]]
             [mongo :refer [new-mongo-db]]
             [sente :refer [new-channel-sockets]]]
            [system.core :refer [defsystem]]
            [taoensso.sente.packers.transit :as sente-transit]
            [taoensso.sente.server-adapters.http-kit
             :refer
             [sente-web-server-adapter]]
            [taoensso.timbre :as log]
            [tully
             [handler :refer [event-msg-handler* secure-routes]]
             [influx :refer [new-influx-db]]
             [metrics-requester :refer [new-metrics-requester]]
             [metrics-manager :refer [new-scheduler new-metrics-manager]]])
  (:import org.bson.types.ObjectId))

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

(defn parse-number
  "Reads a number from a string. Returns nil if not a number."
  [s]
  (if (re-find #"^-?\d+\.?\d*$" s)
    (edn/read-string s)))

(defsystem dev-system
  [;; base infrastructure components
   :store (new-mongo-db (env :mongo-host) (parse-number (env :mongo-port)) (env :mongo-db) {})
   ;; disabled for now for port conflict
   :influx (new-influx-db (env :influx-host) (parse-number (env :influx-port)) (env :influx-db))
   :scheduler (new-scheduler)
   ;; disabled for now for restart nullpointerexception
   :middleware (new-middleware {:middleware [[wrap-defaults site-defaults]]})

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
                (new-web-server (parse-number (env :web-port)))
                [:handler])
   :sente (new-channel-sockets event-msg-handler* sente-web-server-adapter
                               {:packer (sente-transit/get-transit-packer
                                         :json
                                         {:handlers {ObjectId objectid-writer}}
                                         {:handlers {"object-id" objectid-reader}}
                                         )
                                :user-id-fn (fn [ring-req] (first (str/split (:client-id ring-req) #"-")))
                                })
   
   :metrics-requester (component/using
                       (new-metrics-requester)
                       {:influx-component :influx
                        :request-chan :metrics-request-chan})

   :metrics-manager (component/using
                     (new-metrics-manager)
                     [:store :influx :scheduler :metrics-requester])
   ])
