(ns tully.spec
  (:require [clojure.spec :as s]))

(s/def :tully/doi string?)
(s/def :tully/cmd #{:tully/get-scholar-count})
(s/def :tully/request-msg (s/keys :req [:tully/cmd :tully/doi]))
