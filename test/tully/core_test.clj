(ns tully.core-test
  (:require [clojure.test :refer :all]
            [clj-time.core :as time]
            [tully.metrics-manager :as tmm]))


(deftest test-due-dois
  (let [dois ["doi1" "doi2" "doi3"]
        times [(time/date-time 2000 10 11 10)
               (time/date-time 2000 10 11 16)
               (time/date-time 2000 10 10)]
        now (time/date-time 2000 10 11 19)
        interval (-> 3 time/hours)
        due-dois (tmm/due-dois dois times now interval)]
    (is (= due-dois ["doi1" "doi3"]))))
