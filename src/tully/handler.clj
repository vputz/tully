(ns tully.handler
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [hiccup.core :refer :all]
            [hiccup.page :refer [html5 include-css include-js]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            ))

(defroutes main-routes
  (GET "/foo" []
       (html5 [:head [:title "UR HELLO PAGE"]]
              [:body  [:h1 "Hello"] [:p [:i "hello"] " world" [:div {:id "container"}]]
               (include-js "js/main.js")]))
  (route/resources "/")
  (route/not-found "PAGE NOT FOUND"))

;;compojure resources?  compojure.route/resources "/" serves from resources/public and that's implicit?
(def app
  (-> main-routes
      (wrap-defaults site-defaults)))

