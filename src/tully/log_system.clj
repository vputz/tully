(ns tully.log-system
  (:require [taoensso.timbre :as log]
            [taoensso.timbre.appenders.3rd-party.rolling :as rolling]
            [com.stuartsierra.component :as component]))

(defrecord Log-system
    [stdout-level file-path file-level riemann-host riemann-port riemann-level]
  component/Lifecycle
  (start [component]
    (let [old-appenders (:appenders log/*config*)
          file-appender
          (if (nil? file-path)
            {}
            {:file (rolling/rolling-appender
                    {:path file-path :pattern :daily})})]
      (log/merge-config! {:appenders (merge old-appenders file-appender)})
      (assoc component :old-appenders old-appenders)))

  (stop [component]
    (log/merge-config! {:appenders (:old-appenders component)})
    (dissoc component :old-appenders)))

(defn new-log-system
  ([]
   (map->Log-system {}))
  ([stdout-level]
   (map->Log-system {:stdout-level stdout-level}))
  ([stdout-level file-path file-level]
   (map->Log-system {:stdout-level stdout-level
                    :file-path file-path
                    :file-level file-level}))
  ([stdout-level file-path file-level riemann-host riemann-port riemann-level]
   (map->Log-system {:stdout-level stdout-level
                    :file-path file-path
                    :file-level file-level
                    :riemann-host riemann-host
                    :riemann-port riemann-port
                    :riemann-level riemann-level})))


