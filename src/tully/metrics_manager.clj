(ns tully.metrics-manager
  (:require [clojure.string :as str]
            [clojurewerkz.quartzite
             [conversion :as qc]
             [jobs :as j]
             [scheduler :as qs]
             [triggers :as t]]
            [system.repl :refer [system]]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]))

;; consider reimplementing https://github.com/danielsz/system/blob/master/src/system/components/quartzite.clj




(j/defjob no-op-job
  [ctx]
  (log/debug "No-op-job"))


(j/defjob get-metrics-job
  [ctx]
  (let [m (qc/from-job-data ctx)]
    (log/debug "Running get-metrics job with data " m)))

(defn build-metrics-request
  [doi]
  (j/build
   (j/of-type get-metrics-job)
   (j/using-job-data {"doi" doi})
   (j/with-identity (j/key (str/join ["jobs.get-metrics." doi])))))

(defn submit-job-now
  [job id]
  (let [s (get-in system [:scheduler :scheduler])
        trigger (t/build
                 (t/with-identity (t/key id))
                 (t/start-now))]
    (qs/schedule s job trigger))
  
  )


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

(j/defjob get-metrics-job
  [ctx]
  (let [m (qc/from-job-data ctx)]
    (log/debug "Running scheduling job with data" m)))

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
    (qs/shutdown (:scheduler component))
    component))

(defn new-scheduler
  []
  (map->Scheduler {}))

(defrecord Metrics-manager
                                        ;    ^{""}
    
    [store influx scheduler metrics-requester]
  component/Lifecycle

  (start [this]
    (log/info "Starting metrics-manager")
    ;; drop "start initial schedule project"
    this)

  (stop [this]
    (log/info "Stopping metrics-manager")
    this))

(defn new-metrics-manager
  "Creates a new metrics manager"
  []
  (map->Metrics-manager {}))
