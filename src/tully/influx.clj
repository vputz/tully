(ns tully.influx
  (:require [capacitor.core :as cap]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

(defrecord Influx [host port db]
  component/Lifecycle
  (start [component]
    (do
      (log/info "Starting influx on host " host " port " port " with db " db)
      (let [client (cap/make-client {:db db :host host :port port})]
        (assoc component :client client))))

  (stop [component]
    (do
      (log/info "Stopping influx")
      (dissoc component :client)
      ;; (-> component
      ;;     (dissoc component :client))
      )))


(defn new-influx-db
  ([]
   (map->Influx {}))
  ([host port db]
   (map->Influx {:host host :port port :db db})))

(defn add-doi-cites [component doi cites metric]
               (cap/write-point (:client component) {:measurement metric
                                                 :fields {"value" cites}}))
