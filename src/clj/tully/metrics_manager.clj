(ns tully.metrics-manager
  (:require [clojure.string :as str]
            [clojure.walk :refer [keywordize-keys]]
            [clojurewerkz.quartzite
             [conversion :as qc]
             [jobs :as j]
             [scheduler :as qs]
             [triggers :as t]]
            [com.stuartsierra.component :as component]
            [system.repl :refer [system]]
            [taoensso.timbre :as log]
            [clj-time.core :as time]
            [clj-time.format :as timef]
            [tully
             [influx]
             [scholar :as scholar]]
            [tully.db :as db]))

;; consider reimplementing https://github.com/danielsz/system/blob/master/src/system/components/quartzite.clj




(j/defjob no-op-job
  [ctx]
  (log/debug {:event "no-op"}))


(j/defjob get-metrics-job
  [ctx]
  (let [m (qc/from-job-data ctx)
        doi (get m "doi")
        client (keywordize-keys (get m "client"))]
    (do 
      (log/debug {:event "run-get-metrics" :data m})
      (let [cites (scholar/doi-cites doi)]
        (tully.influx/add-scholar-cites client doi cites)))))

(defn build-metrics-request
  [doi client]
  (j/build
   (j/of-type get-metrics-job)
   (j/using-job-data {"doi" doi "client" client})
   (j/with-identity (j/key (str/join ["jobs.get-metrics." doi])))))





(defn due-dois
  "Returns a sequence of DOIs which are due for updates, in order"
  [dois timestamps now interval]
  (let [pairs (map vector dois timestamps)
        interval-start (time/minus now interval)
        due-pairs (filter #(time/before? (second %) interval-start) pairs)]
    (->> due-pairs
       (sort #(time/after? (second %1) (second %2)))
       (map first))))

(defn mult-interval-from
  "Returns a time which is 'n' 'interval's from 't"
  [n interval t]
  (if (= 0 n)
    t
    (apply (partial time/plus t) (repeat n interval))))


(defn build-simple-trigger
  "Builds a simple trigger for scheduling one job at a particular n intervals from now"
  [multiple doi interval]
  (let [start-time (mult-interval-from multiple interval (time/now))
        trigger-name (str/join "." ["trigger" doi])]
    (log/debug {:event "build-simple-trigger"
                :data {:multiple multiple
                       :interval interval
                       :now (time/now)
                       :start-time start-time
                       :trigger-name trigger-name}})
    (t/build
     (t/with-identity (t/key trigger-name))
     (t/start-at start-time))))



(declare build-schedule-metrics-request)

(j/defjob schedule-metrics-job
  [ctx]
  (let [m (qc/from-job-data ctx)
        client (keywordize-keys (get m "client"))
        doi-db (get m "db")
        scheduler (get m "scheduler")
        interval (get m "recent-interval")
        interval-between (get m "interval-between-requests")
        all-dois (db/get-all-dois doi-db)
        doi-stamps (map #(tully.influx/scholar-last-timestamp client %) all-dois)
        due (due-dois all-dois doi-stamps (time/now) interval)
        jobs (map #(build-metrics-request % client) due)
        triggers (map-indexed #(build-simple-trigger %1 %2 interval-between) due)
        jobpairs (map vector jobs triggers)

        next-schedule-time (mult-interval-from (count jobs) interval (time/now))
        next-schedule-job (build-schedule-metrics-request client doi-db scheduler interval interval-between)
        next-schedule-trigger (t/build
                               (t/with-identity (t/key "trigger.schedule"))
                               (t/start-at next-schedule-time))
        schedule-pair (fn [pair]
                        (let [job (first pair)
                              trigger (second pair)]
                          (log/debug {:event "scheduling-job" :data {:job job :trigger trigger}})
                          (try 
                            (qs/schedule scheduler job trigger)
                            (catch Exception e (log/error {:event "exception" :data {:exception e :job job :trigger trigger}})))))
        ]
    (log/debug {:event "scheduling-dois" :data {:dois  all-dois
                                                :timestamps doi-stamps}})
    (doall (map schedule-pair jobpairs))
    (qs/schedule scheduler next-schedule-job next-schedule-trigger)
    ))

(defn build-schedule-metrics-request
  "Builds a 'schedule metrics gathering' request"
  [client db scheduler recent-interval interval-between-requests]
  (j/build
   (j/of-type schedule-metrics-job)
   (j/using-job-data {"client" client
                      "db" db
                      "scheduler" scheduler
                      "recent-interval" recent-interval
                      "interval-between-requests" interval-between-requests})
   (j/with-identity (j/key (str/join "." ["jobs.schedule-metrics"
                                          (timef/unparse (:date-time timef/formatters) (time/now))]))))
  )


(defn submit-job-now
  [job id]
  (let [s (get-in system [:scheduler :scheduler])
        trigger (t/build
                 (t/with-identity (t/key id))
                 (t/start-now))]
    (qs/schedule s job trigger)))


;; # The Schedule #
;; When the metrics manager is started, it immediately runs the "schedule"
;; job.  This works as follows:
;;
;; 1. Get a set of all DOIs in the database
;; 2. Get the last check date/time for all those DOIs in Influx
;; 3. Filter by which ones have been checked within the last "min check period"
;;    (in other words, we don't need checks more often than X days)
;; 4. Sort these from earliest to latest check ("not checked" is the earliest
;;    possible
;; 5. Schedule all DOIs for a "check metrics" job with a minimum check interval
;;    between them.
;; 6. Add the scheduling job again at the end.

;; # Scheduler #
;; This is a port from
;; Danielsz's system library.  His quartzite scheduler takes a scheduler as 
;; an input record, and shuts it down, but really creates a new one in
;; the start.  This eliminates that.

(defrecord Scheduler []
  component/Lifecycle
  (start [component]
    (let [s (-> (qs/initialize) qs/start)]
      (assoc component :scheduler s)))
  (stop [component]
    (when (:scheduler component)
      (qs/shutdown (:scheduler component)))
    component))

(defn new-scheduler
  []
  (map->Scheduler {}))

(defrecord Metrics-manager
                                        ;    ^{""}
    
    [store influx scheduler metrics-requester recent-interval interval-between-requests]
  component/Lifecycle

  (start [this]
    (log/info {:event "start-metrics-manager"})
    ;; drop "start initial schedule project"
    (let [first-schedule-job (build-schedule-metrics-request
                              (:client influx)
                              (:db store)
                              (:scheduler scheduler)
                              recent-interval
                              interval-between-requests)
          first-schedule-id "schedule.first"
          first-schedule-trigger (t/build
                                  (t/with-identity (t/key first-schedule-id))
                                  (t/start-now))]
      (qs/schedule (:scheduler  scheduler) first-schedule-job first-schedule-trigger))
    this)

  (stop [this]
    (log/info {:event "stop-metrics-manager"})
    this))

(defn new-metrics-manager
  "Creates a new metrics manager"
  []
  (map->Metrics-manager {}))
