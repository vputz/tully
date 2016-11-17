(ns tully-cljs.devcards
  (:require
   [tully-cljs.views :as views]
   [tully-cljs.chsk :as chsk]
   [devcards.core :as dc]
   [taoensso.timbre :as log]
   [re-frame.core :as re-frame])
  (:require-macros
   [devcards.core :refer [defcard defcard-rg]]))


;; some useful items for devcards and re-frame:
;; https://github.com/nberger/devcards/blob/iframe/example_src/devdemos/re_frame.cljs
;; doesn't quite work--added a "reset to test db" button


(defcard-rg paper-card
  "Paper card"
  (views/wrap-for-atom views/paper-component :group-id :paper-id :doi :title)
  {:group-id :group-1 :paper-id :paper-1  :doi "DOI" :title "title"}
  {:inspect-data true})

(defn setup-example-1
  []
  (re-frame/reg-event-db
   :initialize-db
   (fn [db [_ data]]
     (log/info "Initializing db with " data)
     data)))

(def test-db
  {:groups (sorted-map  (chsk/ObjectId. "Vic's Papers") (sorted-map
                                                         :_id (chsk/ObjectId. "Vic's Papers")
                                                         :desc "Vputz's papers"
                                                         :papers {(chsk/ObjectId. "paper 1")
                                                                  {:doi "10.1039/C0SM00164C"
                                                                   :title "Swimmer-tracer scattering at low Reynolds Number"
                                                                   :id (chsk/ObjectId. "paper 1")}

                                                                  (chsk/ObjectId. "paper 2")
                                                                  {:doi "10.1007/s10955-009-9826-x"
                                                                   :title "Hydrodynamic Synchronisation of Model Microswimmers"
                                                                   :id (chsk/ObjectId. "paper 2")}

                                                                  (chsk/ObjectId. "paper 3")
                                                                  {:doi "10.1016/j.chemphys.2010.04.025"
                                                                   :title "CUDA simulations of active dumbbell suspensions"
                                                                   :id (chsk/ObjectId. "paper 3")}}))})

(defn reset-component
  []
  [:div.row [:button.alert.button {:on-click #(re-frame/dispatch [:initialize-db test-db])} "Reset!"]])

(defcard-rg reset
  "Reset the db"
  (fn [data-atom _]
    [reset-component])
  {}
  {})

(defcard-rg editable-paper-card
  "Editable paper card"
  (fn [data-atom _]
    [views/editable-paper-component "Vic's Papers" "paper 1" (:doi @data-atom) (:title @data-atom)])
  {:doi "10.1039/C0SM00164C" :title "Swimmer-tracer scattering at low Reynolds Number"}
  {:inspect-data true
   :iframe true})

(defcard-rg group-atom-card
  "Group atom card"
  (fn [data-atom _]
    [views/group-component "Vic's Papers" (get-in @data-atom [:groups (chsk/ObjectId. "Vic's Papers")])])
  test-db
  {:iframe true})

(defcard-rg groups-card
  "All Groups"
  views/groups-list
  {}
  {})

(defcard-rg test-chsk
  "chsk"
  views/test-chsk-component
  {}
  {})

(defn ^:export main []
  (enable-console-print!)
  (println "Starting devcard ui")
  (setup-example-1)
  (re-frame/dispatch-sync [:initialize-db test-db])
  (chsk/start-router!)
  (dc/start-devcard-ui!))
