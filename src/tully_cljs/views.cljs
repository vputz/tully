(ns tully-cljs.views
  (:require [reagent.core :as reagent]
            [taoensso.timbre :as log]
            [taoensso.sente :as sente]
            [tully-cljs.chsk :as chsk]
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

(defn editable-paper-component [group-id paper-id doi title]
  (println "Editable paper component " doi " " title)
  (let [editing (reagent/atom false)]
    (fn [group-id paper-id doi title]
      [:div {:class (str "row " (when @editing "editing"))}
       [:div {:class (str "view " (when @editing "editing"))}
        [:div.large-4.medium-4.columns
         {:style {:text-overflow "ellipsis"}
          :on-click #(reset! editing true)}
         doi]
        [:div.large-6.medium-6.columns
         {:style {:text-overflow "ellipsis"}}
         title]
        [:div.large-2.medium-2.columns
         [:button.alert.button
          {:on-click #(dispatch [:delete-doi-from-group group-id paper-id])}
          "Delete"]]]
       (when @editing
         [doi-input-component
          {:title doi
           :on-save #(dispatch [:change-doi-of-paper group-id paper-id %])
           :on-stop #(reset! editing false)}])]      )))

(defn group-component [group-id papers]
  (println "Group component " group-id " " papers)
  [:div.row
   [:div.row
    [:div.large-4.medium-4.columns {:style {:background-color "#CCCCCC"}} "DOI"]
    [:div.large-8.medium-8.columns {:style {:background-color "#CCCCCC"}} "Title"]]
   ;; use vector below instead of seq because seq is lazy and react needs nth-able
   (for [[paper-id paper] (apply vector (seq (:papers papers)))]
     (do
       (log/info (.-stringrep paper-id))
       (with-meta 
         [editable-paper-component group-id paper-id (:doi paper) (:title paper)]
         {:key (apply str group-id "-" paper-id)}))
     )]
  )

(defn groups-list
  []
  (let [groups (subscribe [:groups])]
    (fn []
      (println groups)
      [:div.row 
       (doall (for [[group-id papers] (apply vector (seq @groups))]
                 (with-meta [group-component group-id (get @groups group-id)]
                   {:key group-id})))])))

(defn test-chsk-component []
  [:div [:button.button
         {:on-click
          #(chsk/set-user-sets-from-db)}
         "Send Event"]])

(defn app
  []
  [:div
   [:h1 "OMG TEH APP"]])
