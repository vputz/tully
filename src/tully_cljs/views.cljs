(ns tully-cljs.views
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [subscribe dispatch]]))

;; Form-1 component : return the rendered html
;; Form-2 component : a let- introduces local state, then return a function which renders
;; Form-3 component : madness
;; https://github.com/Day8/re-frame/wiki/Creating-Reagent-Components

(defn wrap-for-atom [component & keys]
  (fn [ratom _]
    (let [args (map #(get @ratom %) keys)]
      (apply component args))))

(defn paper-component [doi title]
  (println "A Paper component " doi " " title)
  [:div.row
   [:div.large-4.medium-4.columns {:style {:text-overflow "ellipsis"}} doi]
   [:div.large-6.medium-6.columns {:style {:text-overflow "ellipsis"}} title]])

(defn doi-input-component [{:keys [title on-save on-stop]}]
  (let [val (reagent/atom title)
        stop #(do (reset! val "")
                 (when on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
               (when (seq v) (on-save v))
               (stop))]
    (fn [props]
      [:input (merge (dissoc props :on-save :on-stop)
                     {:type "text"
                      :value @val
                      :auto-focus true
                      :on-blur save
                      :on-change #(reset! val (-> % .-target .-value))
                      :on-key-down #(case (.-which %)
                                     13 (save)
                                     27 (stop)
                                     nil)})])))

(defn editable-paper-component [doi title]
  (println "Editable paper component " doi " " title)
  (let [editing (reagent/atom false)]
    (fn [doi title]
      [:div {:class (str "row " (when @editing "editing"))}
       [:div.view
        [:div.large-4.medium-4.columns
         {:style {:text-overflow "ellipsis"}
          :on-click #(reset! editing true)}
         doi]
        [:div.large-6.medium-6.columns
         {:style {:text-overflow "ellipsis"}}
         title]
        [:div.large-2.medium-2.columns
         [:button.alert.button {:on-click #(println "Destroy " doi)} "Delete"]]]
       (when @editing
         [doi-input-component
          {:title doi
           :on-save #(println "OnSave")
           :on-stop #(reset! editing false)}])]      )))

(defn group-component [group-id papers]
  (println "Group component " group-id " " papers)
  [:div.row
   [:div.row
    [:div.large-4.medium-4.columns {:style {:background-color "#CCCCCC"}} "DOI"]
    [:div.large-8.medium-8.columns {:style {:background-color "#CCCCCC"}} "Title"]]

   (for [paper papers]
     (with-meta 
       [editable-paper-component (:doi paper) (:title paper)]
       {:key (apply str group-id "-" (:doi paper))})
     )]
  )

(defn groups-list
  []
  (let [groups (subscribe :sorted-groups)]
    (fn []
      (for [group-id (keys @groups)]
        [:div [group-component group-id (group-id @groups)]]))))

