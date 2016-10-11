(ns tully-cljs.devcards
  (:require
   [tully-cljs.views :as views]
   [devcards.core :as dc])
  (:require-macros
   [devcards.core :refer [defcard deftest]]))


(defcard paper-card
  (dc/reagent views/paper-component)
  {:doi "DOI" :title "TITLE"}
  {:inspect-data true :history true})

(defcard test-card
  "LOBTER") 

(defcard hello-card)

(defn ^:export main []
  (enable-console-print!)
  (println "Starting devcard ui")
  (dc/start-devcard-ui!))
