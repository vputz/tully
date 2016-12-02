(ns tully.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [cemerick.friend.credentials :as creds]
            [taoensso.timbre :as log])
  (:import [org.bson.types ObjectId]
           [com.mongodb DB WriteConcern]))

(defn make-test-data [db]
  (log/info "Resetting test data in database")
  (let [userid (ObjectId.)
        test-set-id (ObjectId.)
        sat-set-id (ObjectId.)
        test-user {:_id userid
                   :name "vputz"
                   :password-hash (creds/hash-bcrypt "test")
                   :sets [test-set-id sat-set-id]}
        test-set {:_id test-set-id
                  :desc "Swimmers"
                  :papers [{:id (ObjectId.)
                            :doi "10.1039/C0SM00164C"
                            :title "Swimmer-tracer scattering at Low Reynolds Number"}
                           {:id (ObjectId.)
                            :doi "10.1007/s10955-009-9826-x"
                            :title "Hydrodynamic Synchronisation of Model Microswimmers"}
                           {:id (ObjectId.)
                            :doi "10.1016/j.chemphys.2010.04.025"
                            :title "CUDA simulations of active dumbbell suspensions"}]}
        sat-set {:_id sat-set-id
                 :desc "Satellites"
                 :papers [{:id (ObjectId.)
                           :doi "10.1109/TNS.2004.840838"
                           :title "Survey of DSCS-III B-7 differential surface charging"}
                          {:id (ObjectId.)
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
  (log/info "Creating user " username " with password hash " password-hash " to " db))

(defn get-sets [db]
  (mc/find-maps db "sets"))

(defn get-user-sets [db username]
  (map #(mc/find-map-by-id db "sets" %) (:sets (get-user db username))))

(defn group-first-by
  "groups a collection by a function but then only takes the first of each subcollection"
  [group-fn coll]
  (reduce-kv (fn [coll k v] (assoc coll k (first v))) {} (group-by group-fn coll)))

(defn get-user-sets-as-map
  "DB stores in lists and collections; we export to the clojurescript client as map"
  [db username]
  (let [sets (get-user-sets db username)
        gsets (group-first-by :_id sets)]
    (reduce-kv (fn [coll k v] (assoc coll k (update v :papers (partial group-first-by :id)))) {} gsets)
    ))


(defn user-exists [db username]
  (not (nil? (get-user db username))))
