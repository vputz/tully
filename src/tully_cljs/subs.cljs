(ns tully-cljs.subs
  (:require [re-frame.core :refer [reg-sub subscribe]]))



(defn sorted-groups
  [db _]
  (:groups db))
(reg-sub :sorted-groups sorted-groups)

