(ns tully-cljs.events
  (:require [re-frame.core :refer [reg-event-db reg-event-fx reg-fx after path debug trim-v inject-cofx reg-cofx]]
            [taoensso.timbre :as log]
            [tully-cljs.chsk :as chsk]
            [tully-cljs.db :refer [default-value
                                   check-and-throw
                                   groups->local-store]]))

;; interceptor allows us to check the db's spec
(def check-spec-interceptor (after (partial check-and-throw :tully-cljs.db/db)))

(defn db-interceptors
  "Gets a standard list of interceptors with a given path"
  [new-path]
  [check-spec-interceptor
   (path new-path)
   (when ^boolean js/goog.DEBUG debug)
   trim-v])


(def groups-interceptors (db-interceptors :groups))

(def metrics-interceptors (db-interceptors :group-metrics))

(def panel-interceptors (db-interceptors :active-panel))

;; from clojure.core.incubator,
;; https://github.com/clojure/core.incubator/blob/master/src/main/clojure/clojure/core/incubator.clj
(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(reg-event-fx
 :delete-group
 [trim-v]
 (fn [cofx [group-id]]
   (log/debug "Deleting group " group-id)
   (let [db (:db cofx)
         groups (:groups db)
         newgroups (dissoc groups group-id)]
     {:delete-group group-id
      :write-groups newgroups})
   ))

(reg-event-fx
 :delete-doi-from-group
 [trim-v]
 (fn [cofx [group-id paper-id]]
   ;;(log/debug "groups: " groups)
   (log/debug "Deleting paper " paper-id " from group " group-id)
   (let [db (:db cofx)
         groups (:groups db)
         paper (get-in groups [group-id :papers paper-id])
         newgroups 
         (dissoc-in groups [group-id :papers paper-id])]
     (log/debug "Paper found " paper)
     (log/debug "New groups " newgroups)
     {:write-groups newgroups})))

(reg-event-db
 :initialize-db
 (fn [db [_ data]]
   (log/info "Initializing db with " data)
   data))

(reg-event-db
 :set-groups
 groups-interceptors
 (fn [groups [newgroups]]
   (do
     (log/debug "Setting groups: " newgroups)
     newgroups)))

(reg-event-db
 :set-active-panel
 panel-interceptors
 (fn [panel [new-panel]]
   (do
     (log/debug "Setting active panel to " new-panel)
     new-panel)))

(reg-event-fx
 :add-new-paper
 [trim-v]
 (fn [cofx [group-id new-paper-doi new-paper-title :as args]]
   (log/debug "Adding new paper " args)
   {:write-new-paper-to-db args}
   ))

(reg-fx
 :write-new-paper-to-db
 (fn [[group-id paper-doi paper-title :as args]]
   (chsk/write-new-paper-to-db group-id paper-doi paper-title)))

(reg-event-fx
 :change-doi-of-paper
 [trim-v]
 (fn [cofx [group-id paper-id new-doi :as args]]
   (log/debug args)
   (log/debug "Changing paper " paper-id " in group " group-id " doi to " new-doi)
   (let [db (:db cofx)
         groups (:groups db)
         ]
     (log/debug "groups: " groups)
     (log/debug "group id: " group-id)
     (let [newgroups (assoc-in groups [group-id :papers paper-id :doi] new-doi)]
       {:write-groups newgroups}))))

(reg-event-fx
 :request-user-sets-from-db
 [trim-v]
 (fn [cofx]
   {:request-user-sets-from-db-fx nil}))

(reg-event-fx
 :add-new-group
 [trim-v]
 (fn [cofx [group-name :as args]]
   {:create-new-group-in-db group-name}))

(reg-fx
 :create-new-group-in-db
 (fn [group-name]
   (log/debug "Creating new group named " group-name)
   (chsk/create-new-group-in-db group-name)))

(reg-fx
 :delete-group
 (fn [group-id]
   (log/debug "Deleting group " group-id " from db")
   (chsk/delete-group-id-from-db group-id)))

(reg-fx
 :write-groups
 (fn [newgroups]
   (log/debug "Write Groups:" newgroups)
   (chsk/write-user-groups-to-db newgroups)))

(reg-fx
 :request-user-sets-from-db-fx
 (fn []
   (chsk/request-and-set-user-sets-from-db)))

(reg-event-db
 :set-user-sets-from-db
 groups-interceptors
 (fn [groups [new-groups]]
   (log/debug "Replacing old groups with new groups " new-groups)
   new-groups))

(reg-event-db
 :set-user-metrics-from-db
 metrics-interceptors
 (fn [metrics [new-metrics]]
   (log/debug "Replacing old metrics with new metrics " new-metrics)
   new-metrics))
