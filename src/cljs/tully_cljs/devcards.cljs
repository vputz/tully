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
                                                                   :did (chsk/ObjectId. "paper 1")}

                                                                  (chsk/ObjectId. "paper 2")
                                                                  {:doi "10.1007/s10955-009-9826-x"
                                                                   :title "Hydrodynamic Synchronisation of Model Microswimmers"
                                                                   :did (chsk/ObjectId. "paper 2")}

                                                                  (chsk/ObjectId. "paper 3")
                                                                  {:doi "10.1016/j.chemphys.2010.04.025"
                                                                   :title "CUDA simulations of active dumbbell suspensions"
                                                                   :did (chsk/ObjectId. "paper 3")}}))
   :group-metrics (sorted-map (chsk/ObjectId. "Vic's Papers")
                              (sorted-map :_id (chsk/ObjectId. "Vic's Papers")
                                          :desc "VPutz's Papers"
                                          :metrics
                                          '({"10.1039/C0SM00164C" 0, "10.1007/s10955-009-9826-x" 0, "10.1016/j.chemphys.2010.04.025" 0, :t "2017-01-03T00:00:00Z"}
                                            {"10.1039/C0SM00164C" 0, "10.1007/s10955-009-9826-x" 0, "10.1016/j.chemphys.2010.04.025" 0, :t "2017-01-04T00:00:00Z"}
                                            {"10.1039/C0SM00164C" 0, "10.1007/s10955-009-9826-x" 43.5, "10.1016/j.chemphys.2010.04.025" 0, :t "2017-01-05T00:00:00Z"}
                                            {"10.1039/C0SM00164C" 32, "10.1007/s10955-009-9826-x" 23, "10.1016/j.chemphys.2010.04.025" 18, :t "2017-01-06T00:00:00Z"}
                                            {"10.1039/C0SM00164C" 32, "10.1007/s10955-009-9826-x" 23, "10.1016/j.chemphys.2010.04.025" 18, :t "2017-01-07T00:00:00Z"}
                                            {"10.1039/C0SM00164C" 32, "10.1007/s10955-009-9826-x" 23, "10.1016/j.chemphys.2010.04.025" 18, :t "2017-01-08T00:00:00Z"}
                                            {"10.1039/C0SM00164C" 32, "10.1007/s10955-009-9826-x" 23, "10.1016/j.chemphys.2010.04.025" 18, :t "2017-01-09T00:00:00Z"}
                                            {"10.1039/C0SM00164C" 32, "10.1007/s10955-009-9826-x" 23, "10.1016/j.chemphys.2010.04.025" 18, :t "2017-01-10T00:00:00Z"})))
   :active-panel :groups-panel})

(defn reset-component
  []
  [:div.row [:button.alert.button {:on-click #(re-frame/dispatch [:initialize-db test-db])} "Reset!"]])

(defcard-rg reset
  "Reset the re-frame db"
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

(defcard-rg test-refresh
  "chsk"
  views/test-refresh-component
  {}
  {})

(defcard-rg test-refresh
  "chsk"
  views/test-reset-database-component
  {}
  {})

(defcard-rg graph-component
  "Graph component"
  (fn [data-atom _]
    [views/graph-component (:width @data-atom) (:height @data-atom) (:points @data-atom)])
  {
   :points [{:t "2017-01-04T00:00:00Z" "10.1007/s10955-009-9826-x" 0 "10.1039/C0SM00164C" 0}
            {:t "2017-01-05T00:00:00Z" "10.1007/s10955-009-9826-x" 1 "10.1039/C0SM00164C" 1}
            {:t "2017-01-06T00:00:00Z" "10.1007/s10955-009-9826-x" 2 "10.1039/C0SM00164C" 4}
            {:t "2017-01-07T00:00:00Z" "10.1007/s10955-009-9826-x" 3 "10.1039/C0SM00164C" 9}
            {:t "2017-01-08T00:00:00Z" "10.1007/s10955-009-9826-x" 4 "10.1039/C0SM00164C" 16}
            {:t "2017-01-09T00:00:00Z" "10.1007/s10955-009-9826-x" 5 "10.1039/C0SM00164C" 25}]
   :width 640
   :height 320}
  {:inspect-data false
   :iframe true})


(defcard-rg group-metrics-card
  "Group metrics card"
  (fn [data-atom _]
    [views/group-metrics-graph "Vic's Papers" 640 320 (get-in @data-atom [:group-metrics (chsk/ObjectId. "Vic's Papers")])])
  test-db
  {:iframe true})


(defcard-rg group-metrics-card
  "All Groups Metrics"
  [views/groups-metrics-list {:width 640 :height 320}]
  {}
  {})


(defn ^:export main []
  (enable-console-print!)
  (println "Starting devcard ui")
  ;;  (setup-example-1)
  (re-frame/dispatch-sync [:initialize-db test-db])
  (chsk/make-chsk-sockets "devcards")
  (chsk/start-router!)
  (dc/start-devcard-ui!))
