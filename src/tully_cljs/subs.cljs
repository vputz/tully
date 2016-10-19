(ns tully-cljs.subs
  (:require [re-frame.core :refer [reg-sub subscribe]]))



(defn groups
  [db _]
  (:groups db))
(reg-sub :groups groups)

