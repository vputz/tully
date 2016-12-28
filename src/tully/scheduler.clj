(ns tully.scheduler
  (:require [taoensso.timbre :as log]
            [clojurewerkz.quartzite.scheduler :as qs]
            [system.repl :refer [system]]
            [clojure.string :as str]
            [clojurewerkz.quartzite.jobs :as j]
            [clojurewerkz.quartzite.triggers :as t]
            [clojurewerkz.quartzite.conversion :as qc]
            [clojurewerkz.quartzite.schedule.simple :refer [schedule with-repeat-count with-interval-in-milliseconds]]))

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
                      (t/start-now)
)]
    (qs/schedule s job trigger))
  
  )
