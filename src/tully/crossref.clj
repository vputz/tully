(ns tully.crossref
  (:require [cemerick.url :refer [url url-encode map->query]]
            [taoensso.timbre :as log]
            [clojure.data.json :as json]
            [org.httpkit.client :as http]))

;; https://github.com/crossref/rest-api-doc/blob/master/rest_api.md
;; http://tdmsupport.crossref.org/researchers/
;; http://tdmsupport.crossref.org/researcher-faq/

(defn crossref-doi-url [doi]
  (url "http://api.crossref.org/works" (url-encode doi)))

(defn crossref-doi-response [doi]
  (http/get (str (crossref-doi-url doi))))

(defn sync-title [doi]
  (log/info "Getting title for doi " doi)
  (let [resp @(crossref-doi-response doi)]
    (if (= 404 (:status resp))
      {:valid-lookup false :title "Title not found"}
      {:valid-lookup true :title (first (get-in (json/read-str (:body resp)) ["message" "title"]))})))
