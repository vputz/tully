(ns tully.http-kit-server
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :refer [run-server]]))

(defrecord WebServer [options server handler]
  component/Lifecycle
  (start [component]
    (let [handler (get-in component [:handler :handler] handler)
          server (run-server handler options)]
      (assoc component :server server)))
  (stop [component]
    (when server
      (server)
      (Thread/sleep 200)
      component)))

;;
;; (def Options
;;   {(s/optional-key :ip) sc/IpAddress
;;    (s/optional-key :port) sc/Port
;;    (s/optional-key :thread) sc/PosInt
;;    (s/optional-key :worker-name-prefix) s/Str
;;    (s/optional-key :queue-size) sc/PosInt
;;    (s/optional-key :max-body) sc/PosInt
;;    (s/optional-key :max-line) sc/PosInt})

(defn new-web-server
  ([port]
   (new-web-server port nil {}))
  ([port handler]
   (new-web-server port handler {}))
  ([port handler options]
   (map->WebServer {:options 
                    (merge {:port port}
                           options)
                    :handler handler})))
