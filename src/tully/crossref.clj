(ns tully.crossref
  (:require [cemerick.url :refer [url url-encode map->query]]
            [org.httpkit.client :as http]))

;; https://github.com/crossref/rest-api-doc/blob/master/rest_api.md
;; http://tdmsupport.crossref.org/researchers/
;; http://tdmsupport.crossref.org/researcher-faq/

(defn test-fn [] :test)
