(ns tully-cljs.core
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [taoensso.timbre :as log]
            [taoensso.sente :as sente]
            [tully-cljs.events]
            [tully-cljs.subs]
            [tully-cljs.chsk :as chsk]
            [tully-cljs.views]))


(log/infof "Clojurescript loaded correctly!")

(defn get-username
  []
  (-> (.getElementById js/document "server-data")
     (.getAttribute "username")
     (cljs.reader/read-string)))

(defn ^:export main []
  (enable-console-print!)
  (let [username (get-username)]
    (chsk/make-chsk-sockets "devcards")
;    (chsk/wait-for-msg chsk/ch-chsk)
    (chsk/start-router!))
  (reagent/render [tully-cljs.views/app]
                  (.getElementById js/document "app"))  
 
)
