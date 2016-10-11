(ns tully.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [cemerick.friend.credentials :as creds]
            [taoensso.timbre :as log])
  (:import [org.bson.types ObjectId]
           [com.mongodb DB WriteConcern]))

(defn make-test-data [db]
  (let [userid (ObjectId.)
        setid (ObjectId.)
        test-user {:_id userid
                   :name "vputz"
                   :password-hash (creds/hash-bcrypt "test")
                   :sets [setid]}
        test-set {:_id setid
                  :desc "VPutz's Papers"
                  :papers [{:doi "10.1039/C0SM00164C"
                            :title "Swimmer-tracer scattering at Low Reynolds Number"}
                           {:doi "10.1007/s10955-009-9826-x"
                            :title "Hydrodynamic Synchronisation of Model Microswimmers"}
                           {:doi "10.1016/j.chemphys.2010.04.025"
                            :title "CUDA simulations of active dumbbell suspensions"}
                           {:doi "10.1109/TNS.2004.840838"
                            :title "Survey of DSCS-III B-7 differential surface charging"}
                           {:doi "10.1109/TNS.2007.909911"
                            :title "Bootstrap Surface Charging at GEO: Modeling and On-Orbit Observations From the DSCS-III B7 Satellite"}]}]
    (mc/remove db "users")
    (mc/insert db "users" test-user)

    (mc/remove db "sets")
    (mc/insert db "sets" test-set)
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

(defn user-exists [db username]
  (not (nil? (get-user db username))))
