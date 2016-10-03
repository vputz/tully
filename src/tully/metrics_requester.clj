(ns tully.metrics-requester
  (:require [clojure.core.async :refer [go >! <! chan go-loop alt!]]
            [taoensso.timbre :as log]
            [clojure.spec :as s]
            [tully.spec :refer :all]
            [tully.scholar :as scholar]
            [com.stuartsierra.component :as component]))


(defn process-msg [component msg]
  (if (s/valid? :tully/request-msg msg)
    (do
      (log/info "processing msg " msg)
      (let [{cmd :tully/cmd doi :tully/doi} msg]
        (when (= cmd :tully/get-scholar-count)
          (log/info "Finding count for doi " doi)
          (let [cites (scholar/doi-cites doi)]
            (log/info cites))
          )
        ))
    (log/error "Bad command to requester:" msg)))


(defrecord Metrics-requester [influx-component request-chan]
  component/Lifecycle

  (start [this]
    (log/info "Starting metrics requester")
    ;; pattern from https://christopherdbui.com/a-tutorial-of-stuart-sierras-component-for-clojure/; perhaps encapsulate
    (let [stop-chan (chan 1)]
      (go-loop []
        (alt!
          request-chan
          ([msg]
           (log/info "metrics-requester recv " msg)
           (process-msg this msg)
           (recur))
          stop-chan
          ([_]
           (log/info "metrics-requester recv STOP")
           :no-op))))
    )

  (stop [this]
    (log/info "Stopping metrics requester")
    this))

(defn new-metrics-requester []
  (map->Metrics-requester {}))


