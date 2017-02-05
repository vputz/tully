(ns tully.scholar
  (:require [cemerick.url :refer [url]]
            [clojure.string :as str]
            [net.cgrand.enlive-html :as html]
            [org.httpkit.client :as http]))

                                        ; much from http://masnun.com/2016/03/20/web-scraping-with-clojure.html


(defn doi-url [doi]
  (-> "http://scholar.google.com/scholar"
     url
     (assoc :query {:hl "en" :q doi})))

(defn get-dom [doi]
  (:body @(http/get (str (doi-url doi)))))

(defn cites [body]
  (let [cite-strings
        (as-> body b
          (html/html-snippet b)
          (html/select b [:div.gs_fl :a])
          (map (comp first :content) b)
          (filter #(and (string? %) (str/starts-with? % "Cited by")) b))]
    (when (pos? (count cite-strings))
      (->> cite-strings
          first
          (re-seq #"[\d]+")
          first
          Integer.))))

(defn doi-cites [doi]
  (cites (get-dom doi)))
