(ns tully.systems
  (:require [clojure
             [edn :as edn]
             [string :as str]]
            [clojure.core.async :refer [chan]]
            [cognitect.transit :as transit]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [system.repl :refer [system set-init! start]]
            [system.components
             [endpoint :refer [new-endpoint]]
             [handler :refer [new-handler]]
             [http-kit :refer [new-web-server]]
             [middleware :refer [new-middleware]]
             [mongo :refer [new-mongo-db]]
             [sente :refer [new-channel-socket-server]]]
            [system.core :refer [defsystem]]
            [taoensso.sente.packers.transit :as sente-transit]
            [taoensso.sente.server-adapters.http-kit
             :refer
             [sente-web-server-adapter]]
            [taoensso.timbre :as log]
            [clj-time.core :as time]
            [tully
             [handler :refer [event-msg-handler* secure-routes]]
             [influx :refer [new-influx-db]]
             [metrics-requester :refer [new-metrics-requester]]
             [metrics-manager :refer [new-scheduler
                                      new-metrics-manager
                                      map->Metrics-manager]]])
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

(defn parse-seconds
  "reads a number from a string; returns clj-time seconds"
  [s]
  (-> s
     parse-number
     time/seconds))



(defsystem dev-system
  [;; base infrastructure components
   :store (new-mongo-db (env :tully-mongo-host)
                        (parse-number (env :tully-mongo-port))
                        (env :tully-mongo-db)
                        {}
                        (env :tully-mongo-user)
                        (env :tully-mongo-pass))
   ;; disabled for now for port conflict
   :influx (new-influx-db (env :tully-influx-host) (parse-number (env :tully-influx-port)) (env :tully-influx-db) (env :tully-influx-user) (env :tully-influx-pass))
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
                (new-web-server (parse-number (env :tully-web-port)))
                [:handler])
   :sente (new-channel-socket-server event-msg-handler* sente-web-server-adapter
                                     {:type :auto
                                      :packer (sente-transit/get-transit-packer
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
                     (map->Metrics-manager
                      {:recent-interval (parse-seconds (env :tully-recent-interval))
                       :interval-between-requests (parse-seconds (env :tully-interval-between-requests))})
                     [:store :influx :scheduler :metrics-requester]
                     )
   
   ])

(defn serve-req [req]
  (if (nil? system)
    (let [system #'dev-system]
      (set-init! system)
      (start)))
  (let [handler (get-in system [:handler :handler])]
    (handler req)))
