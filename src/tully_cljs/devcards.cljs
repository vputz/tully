(ns tully-cljs.devcards
  (:require
   [tully-cljs.views :as views]
   [devcards.core :as dc])
  (:require-macros
   [devcards.core :refer [defcard defcard-rg]]))


(defcard-rg paper-card
  "Paper card"
  (views/wrap-for-atom views/paper-component :doi :title)
  {:doi "DOI" :title "title"}
  {:inspect-data true})

(defcard-rg editable-paper-card
  "Editable paper card"
  (fn [data-atom _]
    [views/editable-paper-component (:doi @data-atom) (:title @data-atom)])
  {:doi "DOI1" :title "TITLE"}
  {:inspect-data true})

(defcard-rg group-atom-card
  "Group atom card"
  (fn [data-atom _]
    [views/group-component (:group-id @data-atom) (:papers @data-atom)])
  {:group-id "Vic's Papers"
   :papers [{:doi "10.1039/C0SM00164C"
             :title "Swimmer-tracer scattering at low Reynolds Number"}
            {:doi "10.1007/s10955-009-9826-x"
             :title "Hydrodynamic Synchronisation of Model Microswimmers"}
            {:doi "10.1016/j.chemphys.2010.04.025"
             :title "CUDA simulations of active dumbbell suspensions"}]})

(defn ^:export main []
  (enable-console-print!)
  (println "Starting devcard ui")
  (dc/start-devcard-ui!))
