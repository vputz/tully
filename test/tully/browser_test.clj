(ns tully.browser-test
  (:require [clojure.test :refer :all]
            [environ.core :refer [env]]
            [webica.core :as w]
            [webica.by :as by]
            [webica.web-driver :as driver]
            [webica.chrome-driver :as chrome]
            [webica.web-element :as element]
            [webica.web-driver-wait :as wait]
            [webica.remote-web-driver :as browser]))

(defn with-browser [t]
  (System/setProperty "webdriver.chrome.driver" (env :chromedriver-path))
  (chrome/start-chrome)
  (t)
  (browser/quit))

(use-fixtures :once with-browser)

(deftest lmgtfy
  (browser/get "http://www.google.com"))
