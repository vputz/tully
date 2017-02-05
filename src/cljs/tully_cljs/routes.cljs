(ns tully-cljs.routes
  (:require [bidi.bidi :as bidi]
            [pushy.core :as pushy]
            [re-frame.core :as re-frame]))

;; # Routing #
;; Routing during the main page interactions is done with bidi and pushy;
;; see https://pupeno.com/2015/08/26/no-hashes-bidirectional-routing-in-re-frame-with-bidi-and-pushy/

(def routes ["/tully/main/" {"" :groups
                             "metrics" :metrics
                             "options" :options}])

(defn parse-url
  "parse urles using bidi"
  [url]
  (bidi/match-route routes url))


(defn dispatch-route
  "Dispatches a route to change the main re-frame panel"
  [matched-route]
  (let [panel-name (keyword (str (name (:handler matched-route)) "-panel"))]
    (re-frame/dispatch [:set-active-panel panel-name])))


(defn app-routes []
  (pushy/start! (pushy/pushy dispatch-route parse-url)))

(def url-for (partial bidi/path-for routes))
