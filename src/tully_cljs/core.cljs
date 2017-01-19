(ns tully-cljs.core
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [taoensso.timbre :as log]
            [taoensso.sente :as sente]
            [tully-cljs.events]
            [tully-cljs.subs]
            [tully-cljs.chsk :as chsk]
            [tully-cljs.routes :refer [app-routes]]
            [tully-cljs.views]))


(log/infof "Clojurescript loaded correctly!")

(defn get-username
  []
  (-> (.getElementById js/document "server-data")
     (.getAttribute "username")
     (cljs.reader/read-string)))

(def empty-db
  {:groups {}
   :group-metrics {}
   :active-panel :groups-panel})

(defn ^:export main []
  (enable-console-print!)
  ;;  (let [username (get-username)])
  ;; enable foundation on our page
  
  (dispatch-sync [:initialize-db empty-db])
  ;; get username from server-data element
  (let [username (.. (js/$ "div#server-data")
                    (attr "username"))]
    (chsk/make-chsk-sockets username))
  (chsk/start-router!)
  (app-routes)
  (reagent/render [tully-cljs.views/app]
                  (.getElementById js/document "app"))
  (log/debug "Running foundation on document")
  (.. (js/$ js/document)
     foundation)
  )
