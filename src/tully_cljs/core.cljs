(ns tully-cljs.core
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [tully-cljs.events]
            [tully-cljs.subs]
            [tully-cljs.views]))

(enable-console-print!)

(println "hello, wurld!")

(defn ^:export main []
  (dispatch-sync [:initialise-db])
  (reagent/render [tully-cljs.views/app]
                  (.getElementById js/document "app")))
