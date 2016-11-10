(ns tully-cljs.core
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [taoensso.timbre :as log]
            [taoensso.sente :as sente]
            [tully-cljs.events]
            [tully-cljs.subs]
            [tully-cljs.views]))


(log/infof "Clojurescript loaded correctly!")

(defn ^:export main []
  (dispatch-sync [:initialise-db])
  (reagent/render [tully-cljs.views/app]
                  (.getElementById js/document "app")))
