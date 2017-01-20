(ns tully.log-system
  (:require [taoensso.timbre :as log]
            [com.startsierra.component :as component]))

(defrecord Log-system
    [stdout-level file-path file-level riemann-host riemann-port riemann-level]
  component/Lifecycle
  (start [component]
    (let [old-appenders (:appenders log/*config*)
          file-appender
          (if (nil? file-name)
            {}
            {:file (log.appenders.3rd-party.rolling/rolling-appender
                    {:path file-path :pattern :daily})})]
      (log/merge-config! {:appenders (merge old-appenders file-appender)})
      (assoc component :old-appenders old-appenders)))

  (stop [component]
    (log/merge-config! {:appenders old-appenders})
    (dissoc component :old-appender)))



