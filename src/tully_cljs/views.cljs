(ns tully-cljs.views
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [subscribe dispatch]]))

;; Form-1 component : return the rendered html
;; Form-2 component : a let- introduces local state, then return a function which renders
;; Form-3 component : madness
;; https://github.com/Day8/re-frame/wiki/Creating-Reagent-Components

(defn paper-component [{:keys [doi title]}]
  [:li [:div  [:label doi] [:label title]]])

(defn group-component [group-id papers]
  [:ul (for [paper papers]
         ^{:key (:doi paper)} [paper-component paper])])

(defn groups-list
  []
  (let [groups (subscribe :sorted-groups)]
    (fn []
      (for [group-id (keys @groups)]
        [:div [group-component group-id (group-id @groups)]]))))

