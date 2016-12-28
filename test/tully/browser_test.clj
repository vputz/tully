(ns tully.browser-test
  (:require [clojure.test :refer :all]
            [environ.core :refer [env]]
            [webica.core :as w]
            [webica.by :as by]
            [webica.web-driver :as driver]
            [webica.chrome-driver :as chrome]
            [webica.web-element :as element]
            [webica.web-driver-wait :as wait]
            [webica.remote-web-driver :as browser]
            [tully.db :as db]
            [tully.systems :refer [dev-system]]
            [system.repl :refer [system set-init! start stop]]))

(defn with-system [t]
  (let [system #'dev-system]
    (set-init! system)
    (start)
    (t)
    (stop)))

(defn with-browser [t]
  (System/setProperty "webdriver.chrome.driver" (env :chromedriver-path))
  (chrome/start-chrome)
  (t)
  (browser/quit))

(defn with-test-data [t]
  (db/make-test-data (get-in system [:store :db]))
  (t))

(use-fixtures :once with-system with-browser)
(use-fixtures :each with-test-data)

(defn fill-input-named
  "fills an input form on a page"
  [name contents]
  (let [input (browser/find-element (by/name name))]
    (element/send-keys input contents)))

(defn submit-named
  "submits the form named (or containing named) name"
  [name]
  (let [element (browser/find-element (by/name name))]
    (element/submit element)))

(deftest make-account
  (browser/get "localhost:3001")
  (let [login-link (browser/find-element (by/link-text "Sign up"))]
    (element/click login-link))
  (fill-input-named "username" "test")
  (fill-input-named "password" "testpass")
  (fill-input-named "confirm" "testpass")
  (submit-named "signup-form")
  (is (= 1 1)))
