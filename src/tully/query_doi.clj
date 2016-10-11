(ns tully.query-doi
  (:require [org.httpkit.client :as http]
            [cemerick.url :refer [url url-encode]]))

(defn query-doi-url [doi]
  (url "http://doi.org/api/handles" (url-encode doi)))

(defn query-doi-response [doi]
  (http/get (str (query-doi-url doi))))

(defn crossref-doi-url [doi]
  (url "http://api.crossref.org/works" (url-encode doi)))

