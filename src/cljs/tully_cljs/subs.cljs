(ns tully-cljs.subs
  (:require [re-frame.core :refer [reg-sub subscribe]]))



(defn groups
  [db _]
  (:groups db))

(defn group-metrics
  [db _]
  (:group-metrics db))

(defn active-panel
  [db _]
  (:active-panel db))

(reg-sub :groups groups)
(reg-sub :group-metrics group-metrics)
(reg-sub :active-panel active-panel)
