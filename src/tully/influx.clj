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

(defn escape-doi
  "Escapes slash (and perhaps other characters) in DOIs with two backslashes to be passed in influx queries"
  [doi]
  (clojure.string/replace doi "/" "\\/")
  )

(defn group-metrics-query
  "Creates an influx query string for a group of dois for a period, grouped by interval"
  [dois days-period days-interval]
  (let [edois (map escape-doi dois)
        doi-str (clojure.string/join "|" edois)
        select-str "SELECT mean(value) from scholar_cites"
        doi-clause (clojure.string/join ["doi=~ /" doi-str "/"])
        period-clause (apply str "time > now() - " days-period "d")
        where-clause (apply str "WHERE " doi-clause " AND " period-clause)
        group-clause (apply str "GROUP BY doi, time(" days-interval "d) fill(previous)")
        query-str (clojure.string/join " " [select-str where-clause group-clause])]
    query-str))

(defn influx-time-series-to-vec-series
  "Transforms an influx series (ie {:name .. :tags {:doi doi} ..}) to [{:t t :doi v}]; as well, swaps nil values for 0"
  [s]
  (let [doi (get-in s [:tags :doi])]
    (map #(hash-map :t (first %) doi (if-let [val (second %)] val 0)) (:values s))))



;; In the case of group metrics, two things can go obviously wrong.
;; first, if we ask for metrics on no DOIs at all, the influx default
;; response is to give back metrics on ALL dois, so we manually check and
;; return an empty set.  If we ask for metrics on only DOIs that do not
;; exist in the influx database, get get a result containing no data,
;; so we manually return no data instead of trying to stack an empty set.
(defn group-metrics
  "Gets metrics for a group of dois, for a given period, grouped by a given interval"
  [client dois days-period days-interval]
  (if (= 0 (count dois))
    []
    (let [query-str (group-metrics-query dois days-period days-interval)
          query (cap/db-query client query-str)]
      ;; by now our query has results in influx-speak; we must organize it to work so that each point
      ;; is a map {:t time doi1 val doi2 val}
      ;; TODO what if there are no useful results?
      ;; The query is in the form {:results [{:series [{:name .. :tags {:doi doi} :columns ["time" "mean"]
      ;; :values [[t1, v1] [t2,v2]] but we must get this into the form [{:t t doi1 val1 doi2 val2}] for
      ;; the webpage/d3 stack
      (let [raw-series (-> query
                          :results
                          first
                          :series)]
        (if (nil? raw-series)
          [] ;; in case nothing is returned (dois that don't have entries)
          (let [vec-series (map influx-time-series-to-vec-series raw-series)
                result (map #(apply merge %) (apply map vector vec-series))]
            result))))))
