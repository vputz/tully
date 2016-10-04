(ns tully.handler
  (:require [compojure.core :refer [defroutes routes GET POST]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [hiccup.core :refer :all]
            [hiccup.page :refer [html5 include-css include-js]]
            [hiccup.element :refer [link-to]]
            [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows]
            [ring.util.response :as resp]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [tully.db :as db]
            )
  (:import java.net.URI))


;; pretty-head and pretty-body from cemerick's friend_demo
;; https://github.com/cemerick/friend-demo/blob/master/src/clj/cemerick/friend_demo/misc.clj

(defn pretty-head [title]
  [:head
   (include-css "css/normalize.css")
   (include-css "css/foundation.min.css")
   [:style {:type "text/css"} "ul {padding-left: 2em }"]
   (include-js "js/vendor/jquery.js")
   (include-js "js/vendor/foundation.min.js")
   [:title title]])

(defn pretty-body [& content]
  [:body {:class "row"}
   (into [:div {:class "columns small-12"}] content)])

(defn resolve-uri
  [context uri]
  (let [context (if (instance? URI context) context (URI. context))]
    (.resolve context uri)))

(defn context-uri
  "Resolves a [uri] against the :Context URI (if found) in the provided ring request
  (only useful in conjunction with compojure.core/context).  May not be necessary"
  [{:keys [context]}  uri]
  (if-let [base (and context (str context "/"))]
    (str (resolve-uri base uri))
    uri))

(defn- signup-form
  "creates a sign-up form; 'flash' is used to display an error message such as mismatched passwords"
  [flash]
  [:div {:class "row"}
   [:div {:class "columns small-12"}
    [:h3 "Sign Up"]
    [:div {:class "row"}
     [:form {:method "POST" :action "signup" :class "columns small-4"}
      (anti-forgery-field)
      [:div "Username" [:input {:type "text" :name "username" :required "required"}]]
      [:div "Password" [:input {:type "password" :name "password" :required "required"}]]
      [:div "Confirm Password" [:input {:type "password" :name "confirm" :required "required"}]]
      [:div
       [:input {:type "submit" :class "button" :value "Sign up"}]
       [:span {:style "padding:0 0 0 10px;color:red;"} flash]]]]]])

(def login-form
  [:div {:class "row"}
   [:div {:class "columns small-12"}
    [:h3 "Login"]
    [:div {:class "row"}
     [:form {:method "POST" :action "login" :class "columns small-4"}
      (anti-forgery-field)
      [:div "Username" [:input {:type "text" :name "username"}]]
      [:div "Password" [:input {:type "password" :name "password"}]]]]]])

(defn- create-user
  [{:keys [username password] :as user-data}]
  (-> (dissoc user-data :password)
      (assoc :password-hash (creds/hash-bcrypt password))))

(defn main-routes [{store :store}]
  (routes 
   (GET "/" req
        (html5 (pretty-head "Tully")
               (pretty-body
                [:h1 "Welcome to Tully"]
                [:p (if-let [identity (friend/identity req)]
                      (apply str "Logged in")
                      "Log in for access or sign up below!")]
                (signup-form (:flash req))
                (include-js "js/main.js"))))
   (GET "/login" req
        (html5 (pretty-head "Tully login") (pretty-body login-form)))
   (GET "/logout" req
        (friend/logout* (resp/redirect (str (:context req) "/"))))
   (POST "/signup" {{:keys [username password confirm] :as params} :params :as req}
         (if (and (not-any? str/blank? [username password confirm])
                  (= password confirm))
           (let [user (create-user (into {:db store} (select-keys params [:username :password])))]
             ;; push user into db
             (db/add-user store (:username user) (:password-hash user))
             (friend/merge-authentication
              (resp/redirect (context-uri req username))
              user))
           (assoc (resp/redirect (str (:context req) "/")) :flash (apply str "passwords " password " and " confirm " don't match!"))))
   (GET "/:user" req
        (friend/authenticated
         (let [user (:user (req :params))]
           (if (= user (:username (friend/current-authentication)))
             (html5
              (pretty-head "Welcome")
              (pretty-body
               [:h2 (str "Hello, new user " user "!")]
               [:p "Return to the " (link-to (context-uri req "") " root")
                ", or " (link-to (context-uri req "logout") "log out") "."]))))))
   (route/resources "/")
   (route/not-found "PAGE NOT FOUND")))

(defn tully-credentials [user]
  (log/info "Called credentials fn with user " user))

(defn secure-routes [{store :store}]
  (friend/authenticate
   (main-routes {:store store})
   {:allow-anon? true
    :login-uri "/login"
    :default-landing-uri "/"
    :unauthorized-handler #(-> (html5
                                [:h2 "You do not have sufficient privileges to access " (:uri %)])
                               resp/response
                               (resp/status 401))
    :credential-fn tully-credentials
    :workflows [(workflows/interactive-form)]}))
