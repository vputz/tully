(ns tully-cljs.subs
  (:require [re-frame.core :refer [reg-sub subscribe]]))



(defn groups
  [db _]
  (:groups db))

(defn group-metrics
  [db _]
  (:group-metrics db))

(reg-sub :groups groups)
(reg-sub :group-metrics group-metrics)
