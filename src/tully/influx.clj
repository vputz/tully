(ns tully.influx
  (:require [capacitor.core :as cap]
            [clj-time
             [core :as time]
             [format :as timef]]
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

(defn add-doi-cites
  "write a point to the database; will use current time as timestamp"
  [client doi cites metric]
  (cap/write-point client {:measurement metric
                           :fields {"value" cites}
                           :tags {"doi" doi}}))

(defn add-scholar-cites
  "Adds a Google Scholar citation to the metrics database"
  [client doi cites]
  (add-doi-cites client doi cites "scholar_cites"))

;; # Getting all DOIs in the metrics database #
;; This is actually nontrivial.  To begin with, we can get the Influx
;; results using a query of "SHOW TAG VALUES WITH KEY = doi", but this
;; gives results in the form of
;;
;; {:results
;;   [{:series
;;      [{:name "scholar_cites",
;;        :columns ["key" "value"],
;;        :values [["doi" "DOITHING"]]
;;       }]
;;    }]
;; }
;;
;; thus the long filter below
(defn all-dois
  "Get a set of all the DOIs in the Influx database"
  [client]
  (->> (cap/db-query client "SHOW TAG VALUES WITH KEY = doi")
     :results
     (mapcat :series)
     (mapcat :values)
     (map second)))


(defn scholar-cite-series
  "Gets the citation history from scholar measurements"
  [client doi]
  (let [query-string (str "SELECT time,value FROM scholar_cites WHERE doi='" doi "'")
        query (cap/db-query client query-string)
        formatter (timef/formatters :date-time)]
    (->> query
       :results
       (mapcat :series)
       (mapcat :values)
       (map #(vector (timef/parse formatter (first %)) (second %)))
       )))

(defn scholar-last-timestamp
  "Gets the last timestamp for the given DOI.  If the DOI does not have a timestamp, uses start of the Unix epoch"
  [client doi]
  ( let [query-string (str "SELECT time,value FROM scholar_cites WHERE doi='" doi "' ORDER BY time DESC LIMIT 1")
         query (cap/db-query client query-string)
         formatter (timef/formatters :date-time)
         last-stamp 
         (->> query
            :results
            (mapcat :series)
            (mapcat :values)
            (map #(vector (timef/parse formatter (first %)) (second %)))
            first
            first
            )]
   (if (nil? last-stamp)
     (time/epoch)
     last-stamp))
  )
