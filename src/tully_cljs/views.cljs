(ns tully-cljs.views
  (:require [reagent.core :as reagent]
            [taoensso.timbre :as log]
            [taoensso.sente :as sente]
            [tully-cljs.chsk :as chsk]
            [tully-cljs.routes :as routes]
            [re-frame.core :refer [subscribe dispatch]]
            [cljsjs.d3]
            [clojure.string :as str]))

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
        stop #(when on-stop (on-stop))
        save #(let [v (-> @val str clojure.string/trim)]
               (when (seq v) (on-save v))
               (stop))]
    (fn [props]
      [:input (merge (dissoc props :on-save :on-stop)
                     {:type "text"
                      :value @val
                      :auto-focus true
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
;          :on-click #(reset! editing true)
          }
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

(defn add-paper-component [group-id]
  (let [state (reagent/atom {:valid-lookup false
                             :new-paper-doi ""
                             :new-paper-title ""})
        ;; valid-lookup (reagent/atom false)
        ;; new-paper-doi (reagent/atom "")
        ;; new-paper-title (reagent/atom "")
        ]
    (fn [group-id]
      (let [{:keys [valid-lookup new-paper-doi new-paper-title]} @state]
        [:div.row
         [:div.large-4.medium-4.columns
          [doi-input-component {:title new-paper-doi
                                :on-save #(do (log/info "New paper " %)
                                             (swap! state assoc :new-paper-doi %)
                                             (chsk/get-title-for-doi % state))
                                :on-blur #(let [new-doi (-> % .-target .-value)]
                                           (do (log/debug {:service :tully/new-paper :description (apply str "value changed to " new-doi)})
                                               (swap! state assoc :new-paper-doi new-doi)
                                               (chsk/get-title-for-doi new-doi state)))
                                :on-stop #()}]]
         [:div.large-6.medium-6.columns new-paper-title]
         [:div.large-2.medium-2.columns
          [:button {:class (if valid-lookup "button" "disabled")
                    :disabled (not valid-lookup)
                    :on-click (fn [_]
                                (do
                                  (reset! state {:valid-lookup false
                                                 :new-paper-doi ""
                                                 :new-paper-title ""})
                                  (dispatch [:add-new-paper group-id
                                             new-paper-doi new-paper-title])))
                    ;;    :on-click #((log/debug "Sending new paper" group-id @new-paper-doi @new-paper-title)
                    ;;                                       ;(reset! new-paper-doi "")
                    ;; ;                                      (reset! new-paper-title "")
                    ;;  ;                                     (reset! valid-lookup false)

                    ;;                              ; (log/debug "component reset")
                    ;;                                     ;  (dispatch [:add-new-paper group-id @new-paper-doi @new-paper-title])
                    ;; )
                    }
           (if valid-lookup "Add Paper" "Cannot Add")]]]))))

(defn group-component [group-id group]
  (fn [group-id group]
    [:li.accordion-item {:data-accordion-item true}
     ;;     [:a.accordion-title {:href "#"} (:desc group)]
     [:div ;;.accordion-content {:data-tab-content true}
      [:div.callout.secondary
       [:div.row
        [:div.large-8.medium-8.columns.groupheader (:desc group)]
        [:div.large-2.medium-2.columns
         [:button.alert.button {:on-click (fn [_]
                                            (dispatch [:delete-group group-id]))}
          "Delete Group"]]]
       [:div.row
        [:div.large-4.medium-4.columns.columnheader "DOI"]
        [:div.large-8.medium-8.columns.columnheader  "Title"]]
       ;; use vector below instead of seq because seq is lazy and react needs nth-able
       (for [[paper-id paper] (apply vector (seq (:papers group)))]
         (do
           (log/info (.-stringrep paper-id))
           (with-meta 
             [editable-paper-component group-id paper-id (:doi paper) (:title paper)]
             {:key (apply str group-id "-" paper-id)}))
         )
       (with-meta [add-paper-component group-id] {:key (apply str group-id "-add-paper")})]]])
  )

(defn add-new-group-component
  []
  (let [new-group-name (reagent/atom "New Group")]
    (fn []
      [:div.callout.secondary
       [:div.row
        [:div.large-8.medium-8.columns
         [doi-input-component {:title @new-group-name
                               :on-save #(reset! new-group-name %)
                               :on-blur #(reset! new-group-name (.-value (.-target  %)))
                               :on-stop #()}]]
        [:div.large-4.medium-4.columns
         [:button.button {:on-click (fn [_]
                                      (let [new-group @new-group-name]
                                        (dispatch [:add-new-group new-group])))}
          "Add New Group"]]]])))

(defn groups-list
  []
  (let [groups (subscribe [:groups])]
    (fn []
      (log/info "Number of groups: " (count @groups))
      [:ul.accordion {:data-accordion true  :data-multi-expand true :data-allow-all-closed true}
       (doall (for [[group-id papers] (apply vector (seq @groups))]
                (doall 
                 (log/info "Group id " (.-stringrep group-id))
                 (with-meta [group-component group-id (get @groups group-id)]
                   {:key (.-stringrep group-id)}))))
       [add-new-group-component]])))

(defn test-refresh-component []
  [:div [:button.button
         {:on-click #(dispatch [:request-user-sets-from-db])}
         "Refresh From Server"]])

(defn test-reset-database-component []
  [:div [:button.button.warning
         {:on-click
          #(chsk/reset-test-database)}
         "Reset Test Database"]])



;; # On D3 Components in Clojure/re-frame #
;; This is a bit of confusion.  The best tutorial I've found on the subject leverages
;; React's lifecycle functions as in http://www.szakmeister.net/blog/2015/nov/26/clojurescript-d3-and-reagent/
;; to create the component and then render afterward


(defn graph-component
  "A citation graph component; should handle multiple series in the form of bar graphs"
  [width height points]
  (let [dom-node (reagent/atom nil)]
    (reagent/create-class
     {:reagent-render
      (fn [xs ys]
        ;; we cannot render D3 at this point; must wait for the update.  So include
        ;; the node so that reagent can see we deepnd on it, but jus create stubs
        (log/debug "reagent-render for graph")
        @dom-node
        [:div.graph [:svg {:width width :height height}]]
        )

      :component-did-mount
      (fn [this]
        (let [node (reagent/dom-node this)]
          ;; this should trigger a rerender of the component
          (log/debug "component-did-mount for graph")
          (reset! dom-node node)))

      :component-did-update
      (fn [this old-argv]
        (let [[_ chartwidth chartheight points] (reagent/argv this)
              jpoints (clj->js points)
              times (map :t points)]
          (log/debug "component-did-update for graph:" points)
          (let [                tparse (.. js/d3
                                          (timeParse "%Y-%m-%dT%H:%M:%SZ"))
                begin (tparse (first times))]
            (log/debug "First time: " (first times) "->" begin))
          ;; this is where we actually render the graph
          (let [margin {:top 20 :right 20 :bottom 30 :left 50}
                tparse (.. js/d3
                          (timeParse "%Y-%m-%dT%H:%M:%SZ"))
                cdoi-keys (doall (remove #(= :t %) (keys (first points))))
                doi-keys (clj->js cdoi-keys)
                stacker (.. js/d3
                           stack
                           (keys doi-keys))
                stacked (stacker jpoints)
                width (- chartwidth (:left margin) (:right margin))
                height (- chartheight (:top margin) (:bottom margin))
                xscale (.. js/d3
                          scaleTime
                          (domain (clj->js (vector (tparse (first times)) (tparse (last times)))))
                          (rangeRound #js [0 width])
                                        ;                          (ticks (.. js/d3 timeDay (every 1)))
                          )
                yscale (.. js/d3
                          scaleLinear
                          (domain (clj->js [0 (apply max (flatten (js->clj stacked)))]))
                          (rangeRound #js [height 0]))
                xaxis (.. js/d3
                         axisBottom
                         (scale xscale)
                         (tickSize 5))
                yaxis (.. js/d3
                         axisLeft
                         (scale yscale)
                         )
                vis (.. js/d3
                       (select @dom-node)
                       (select "svg"))
                g (.. vis
                     (append "g")
                     (attr "transform" (str/join ["translate(" (:left margin) "," (:top margin) ")"]))
                     )
                areaGenerator (.. js/d3
                                 area
                                 (x (fn [d i] (xscale (tparse (nth times i)))))
                                 (y0 (fn [d] (yscale (first d))))
                                 (y1 (fn [d] (yscale (second d)))))
                line (.. js/d3
                        line
                        (x (fn [d] (xscale (.-x d))))
                        (y (fn [d] (yscale (.-y d)))))]
            (log/debug "Stacked: " (js->clj stacked))
            (.. vis
               (attr "width" chartwidth)
               (attr "height" chartheight))
            (.. g
               (append "g")
               (attr "class" "axis axis--x")
               (attr "transform" (str/join ["translate(0," height ")"]))
               (call xaxis))
            (.. g
               (append "g")
               (attr "class" "axis axis--y")
               (call yaxis))
            (.. g
               (append "g")
               (selectAll "path")
               (data stacked)
               (enter)
               (append "path")
               (attr "d" areaGenerator)
               (style "fill" (fn [d i] (.. js/d3 (interpolateCool (float (/ i (count points)))))))
               (attr "stroke" "steelblue")
               (attr "stroke-width" "1.5px")
               (attr "data-legend" (fn [d i] (nth cdoi-keys i)))
               )
            (.. vis
               (append "g")
               (attr "class" "legend")
               (attr "transform" (str/join ["translate(" (str (+ 15 (:left margin))) "," (str (:top margin)) ")"] ))
               (style "font-size" "12px")
               (call (.-legend js/d3))
               )
            )
          ))
      })))

(defn group-metrics-graph
  "A graph showing metrics of the papers in a group"
  [group-id width height metric]
  (fn [group-id width height metric]
    [:div.callout.secondary
     [:div.row
      [:div.large-8.medium-8.columns.groupheader (:desc metric)]]
     [graph-component width height (:metrics metric)]])
  )

(defn groups-metrics-list
  [graph-dimensions]
  (let [group-metrics (subscribe [:group-metrics])]
    (fn [graph-dimensions]
      (log/info "Group metrics list for group " @group-metrics)
      [:div.row
       (doall (for [[group-id metrics] (apply vector (seq @group-metrics))]
                (with-meta
                  [group-metrics-graph group-id (:width graph-dimensions) (:height graph-dimensions) metrics]
                  {:key (str (.-stringrep group-id) "-metrics")})))])))


;; # Panels #
;; The use of routing and main panels is as described in a blog entry;
;; see routes.cljs

(defn metrics-panel []
  (fn []
    [groups-metrics-list {:width 640 :height 320}]))

(defn groups-panel []
  (fn []
    [groups-list]))

(defmulti panels identity)

(defn menubar
  []
  (fn []
    [:div.title-bar.sticky {:data-sticky true
                            :data-options {:marginTop "0"}
                            :style {:width "100%"}
                            :data-top-anchor "1"}
     [:div.title-bar-left
      [:ul.dropdown.menu {:data-dropdown-menu true}
       [:li.menu-text "Tully"]
       [:li [:a {:href (routes/url-for :groups)} "Groups"]]
       [:li [:a {:href (routes/url-for :metrics)} "Metrics"]]]]
     [:div.title-bar-right
      [:ul.dropdown.menu {:data-dropdown-menu true}
       [:li [:a {:href "https://github.com/vputz/tully/issues"} "Report Issue"]]
       [:li [:a {:href "/tully/logout"} "Logout"]]]]]))

(defmethod panels :metrics-panel [] [metrics-panel])
(defmethod panels :groups-panel [] [groups-panel])

(defn app
  []
  (let [active-panel (subscribe [:active-panel])]
    (fn []
      [:div
       [:div {:data-sticky-container true}
        [menubar]]
       [panels @active-panel]]))
  ;; [:div
  ;;  [:ul.tabs {:data-tabs true :id "app-tabs"}
  ;;   [:li.tabs-title.is-active [:a {:href "#groups-panel" :aria-selected "true"}
  ;;                              "Groups"]]
  ;;   [:li.tabs-title [:a {:href "#metrics-panel"} "Metrics"]]]
  ;;  [:div.tabs-content {:data-tabs-content "app-tabs"}
  ;;   [:div.tabs-panel.is-active {:id "groups-panel"} [groups-list]]
  ;;   [:div.tabs-panel {:id "metrics-panel"} [groups-metrics-list {:width 640 :height 320}]]]]
  )
