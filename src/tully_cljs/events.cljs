(ns tully-cljs.events
  (:require [re-frame.core :refer [reg-event-db reg-event-fx reg-fx after path debug trim-v inject-cofx reg-cofx]]
            [taoensso.timbre :as log]
            [tully-cljs.chsk :as chsk]
            [tully-cljs.db :refer [default-value
                                   check-and-throw
                                   groups->local-store]]))

;; interceptor allows us to check the db's spec
(def check-spec-interceptor (after (partial check-and-throw :tully-cljs.db/db)))

(def tully-interceptors [check-spec-interceptor
                         (path :groups)
                         (when ^boolean js/goog.DEBUG debug)
                         trim-v])

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
 :delete-doi-from-group
 [trim-v]
 (fn [cofx [group-id paper-id]]
   ;;(log/info "groups: " groups)
   (log/info "Deleting paper " paper-id " from group " group-id)
   (let [db (:db cofx)
         groups (:groups db)
         paper (get-in groups [group-id :papers paper-id])
         newgroups 
         (dissoc-in groups [group-id :papers paper-id])]
     (log/info "Paper found " paper)
     (log/info "New groups " newgroups)
     {:write-groups newgroups})))

(reg-event-db
 :set-groups
 tully-interceptors
 (fn [groups [newgroups]]
   (do
     (log/info "Setting groups: " newgroups)
     newgroups)))

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
   (log/info args)
   (log/info "Changing paper " paper-id " in group " group-id " doi to " new-doi)
   (let [db (:db cofx)
         groups (:groups db)
         ]
     (do 
       (log/info "groups: " groups)
       (log/info "group id: " group-id)
       (let [newgroups (assoc-in groups [group-id :papers paper-id :doi] new-doi)]
         {:write-groups newgroups})))))

(reg-event-fx
 :request-user-sets-from-db
 [trim-v]
 (fn [cofx]
   {:request-user-sets-from-db-fx nil}))

(reg-fx
 :write-groups
 (fn [newgroups]
   (log/info "Write Groups:" newgroups)
   (chsk/write-user-groups-to-db newgroups)))

(reg-fx
 :request-user-sets-from-db-fx
 (fn []
   (chsk/request-and-set-user-sets-from-db)))

(reg-event-db
 :set-user-sets-from-db
 tully-interceptors
 (fn [groups [new-groups]]
   (log/info "Replacing old groups with new groups " new-groups)
   new-groups))
