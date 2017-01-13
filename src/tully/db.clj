(ns tully.db
  (:require [cemerick.friend.credentials :as creds]
            [monger
             [collection :as mc]
             [operators :as mo]]
            [taoensso.timbre :as log])
  (:import org.bson.types.ObjectId))

(defn make-test-data [db]
  (log/info "Resetting test data in database")
  (let [userid (ObjectId.)
        test-set-id (ObjectId.)
        sat-set-id (ObjectId.)
        test-user {:_id userid
                   :name "devcards"
                   :password-hash (creds/hash-bcrypt "test")
                   :sets [test-set-id sat-set-id]}
        test-set {:_id test-set-id
                  :desc "Swimmers"
                  :papers [{:did (ObjectId.)
                            :doi "10.1039/C0SM00164C"
                            :title "Swimmer-tracer scattering at Low Reynolds Number"}
                           {:did (ObjectId.)
                            :doi "10.1007/s10955-009-9826-x"
                            :title "Hydrodynamic Synchronisation of Model Microswimmers"}
                           {:did (ObjectId.)
                            :doi "10.1016/j.chemphys.2010.04.025"
                            :title "CUDA simulations of active dumbbell suspensions"}]}
        sat-set {:_id sat-set-id
                 :desc "Satellites"
                 :papers [{:did (ObjectId.)
                           :doi "10.1109/TNS.2004.840838"
                           :title "Survey of DSCS-III B-7 differential surface charging"}
                          {:did (ObjectId.)
                           :doi "10.1109/TNS.2007.909911"
                           :title "Bootstrap Surface Charging at GEO: Modeling and On-Orbit Observations From the DSCS-III B7 Satellite"}]}]
    (mc/remove db "users")
    (mc/insert db "users" test-user)

    (mc/remove db "sets")
    (mc/insert db "sets" test-set)
    (mc/insert db "sets" sat-set)
    ))

(defn get-user [db username]
  (first (mc/find-maps db "users" {:name username})))

(defn get-verified-user [db username password]
  (when-let [user (get-user db username)]
    (when (creds/bcrypt-verify password (:password-hash user)) user)))

(defn add-user [db username password-hash]
  (log/debug "Creating user " username " with password hash " password-hash " to " db)
  (mc/insert db "users" {:name username :password-hash password-hash :sets []}))

(defn get-sets [db]
  (mc/find-maps db "sets"))

(defn get-user-sets [db username]
  (map #(mc/find-map-by-id db "sets" %) (:sets (get-user db username))))

(defn update-user-sets [db sets username]
  (letfn [(update-set [set]
            (log/info "Updating set " set)
            (mc/update-by-id db "sets" (:_id set) set {:upsert true}))]
    (doall
     (map update-set sets))
    (let [set-ids (map #(:_id %) sets)]
      (mc/update db "users" {:name username} {mo/$set {:sets set-ids}}))))

(defn group-first-by
  "groups a collection by a function but then only takes the first of each subcollection"
  [group-fn coll]
  (reduce-kv (fn [coll k v] (assoc coll k (first v))) {} (group-by group-fn coll)))

(defn get-user-sets-as-map
  "DB stores in lists and collections; we export to the clojurescript client as map"
  [db username]
  (let [sets (get-user-sets db username)
        gsets (group-first-by :_id sets)]
    (reduce-kv (fn [coll k v] (assoc coll k (update v :papers (partial group-first-by :did)))) {} gsets)
    ))

(defn set-papers-map-to-set-papers-seq [set]
  "DB stores in lists, we want to take the sets with papers as map and convert"
  (update-in set [:papers] vals); (assoc set :papers (vals (:papers set)))
  )

(defn user-exists [db username]
  (not (nil? (get-user db username))))

(defn write-paper-to-group [db group-id paper-doi paper-title]
  (let [group (mc/find-map-by-id db "sets" group-id)
        papers (:papers group)
        new-paper {:did (ObjectId.)
                   :doi paper-doi
                   :title paper-title}]
    (mc/update-by-id db "sets" group-id (assoc group :papers (conj papers new-paper)))))

(defn delete-group-id [db group-id]
  (mc/remove-by-id db "sets" group-id))

(defn create-group-for-user [db username group-name]
  (let [new-id (ObjectId.)
        new-set {:_id new-id
                 :desc group-name
                 :papers []}]
    (mc/insert db "sets" new-set)
    (mc/update db "users" {:name username} {mo/$addToSet {:sets new-id}})))

(defn group-dois
  "Gets a vector of DOIs in a group by id"
  [db group-id]
  (let [group (mc/find-map-by-id db "sets" group-id)
        papers (:papers group)
        dois (map :doi papers)]
    dois))


(defn get-all-dois
  "Retrieve a set of all DOIs in the database"
  [db]
  (let [sets (get-sets db) ]
    (->> sets
       (mapcat :papers)
       (map :doi)
       )))
