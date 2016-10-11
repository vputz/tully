(ns tully-cljs.db
  (:require [cljs.spec :as s]
            [re-frame.core :as re-frame]))

;; this is a spec specification for the app-db, which mostly right now
;; consists of a user's groups.  TBD how much data to store in the db (ie
;; if the influx data is elsewhere

(s/def ::group-id string?)
(s/def ::doi string?)
(s/def ::title string?)
;; req-un request unqualified versions so we should be able to have :doi and :title?
(s/def ::paper (s/keys :req-un [::doi ::title]))
(s/def ::papers (s/coll-of ::paper))
(s/def ::groups (s/and
                 (s/map-of ::group-id ::papers)
                 #(instance? PersistentTreeMap %)))

(s/def :::db (s/keys :req-un [::groups]))

(def default-value
  {:groups (sorted-map)})
