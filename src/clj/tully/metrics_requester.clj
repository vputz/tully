(ns tully.metrics-requester
  (:require [clojure.core.async :refer [alt! chan go-loop]]
            [clojure.spec :as s]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [tully.scholar :as scholar]))

(defn process-msg [component msg]
  (if (s/valid? :tully/request-msg msg)
    (do
      (log/debug {:event "processing-msg" :data msg})
      (let [{cmd :tully/cmd doi :tully/doi} msg]
        (when (= cmd :tully/get-scholar-count)
          (log/debug {:event "finding-count" :data doi})
          (let [cites (scholar/doi-cites doi)]
            (log/debug {:event "cites" :data cites}))
          )
        ))
    (log/error "Bad command to requester:" msg)))


(defrecord Metrics-requester [influx-component request-chan]
  component/Lifecycle

  (start [this]
    (log/debug {:event "starting-metrics-requester"})
    ;; pattern from https://christopherdbui.com/a-tutorial-of-stuart-sierras-component-for-clojure/; perhaps encapsulate
    (let [stop-chan (chan 1)]
      (go-loop []
        (alt!
          request-chan
          ([msg]
           (log/debug {:event "metrics-requester-recv" :data msg})
           (process-msg this msg)
           (recur))
          stop-chan
          ([_]
           (log/debug {:event "metrics-requester-recv" :data "STOP"})
           :no-op))))
    )

  (stop [this]
    (log/debug {:event "metrics-requester-stop"})
    this))

(defn new-metrics-requester []
  (map->Metrics-requester {}))


