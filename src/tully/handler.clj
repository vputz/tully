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
            [tully.crossref :as crossref]
            [system.repl :refer [system]]
            )
  (:import java.net.URI
           [org.bson.types ObjectId]))


;; pretty-head and pretty-body from cemerick's friend_demo
;; https://github.com/cemerick/friend-demo/blob/master/src/clj/cemerick/friend_demo/misc.cl

(defn pretty-head [title]
  [:head
   (include-css "css/normalize.css")
   (include-css "css/foundation.min.css")
   (include-css "css/tully.css")
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

(defn- login-form []
  [:div {:class "row"}
   [:div {:class "columns small-12"}
    [:h3 "Login"]
    [:div {:class "row"}
     [:form {:method "POST" :action "login" :class "columns small-4"}
      (anti-forgery-field)
      [:div "Username" [:input {:type "text" :name "username"}]]
      [:div "Password" [:input {:type "password" :name "password"}]]
      [:div [:input {:type "submit" :class "button" :value "Log in"}]]]]]])

(defn- create-user
  [{:keys [username password] :as user-data}]
  (-> (dissoc user-data :password)
      (assoc :password-hash (creds/hash-bcrypt password))))

(defn- redirect-with-flash [req url flash]
  (assoc (resp/redirect (str (:context req) url)) :flash flash))

(defn main-routes [{store :store}]
  (routes 
   (GET "/" req
        (html5 (pretty-head "Tully")
               (pretty-body
                [:h1 "Welcome to Tully"]
                [:p (if-let [identity (friend/identity req)]
                      (apply str "Logged in")
                      [:span  (link-to (context-uri req "signup_form") "Sign up") " to make an account, or log in below!"])]
                (login-form)
                (include-js "js/main.js"))))
   (GET "/login" req
        (html5 (pretty-head "Tully login") (pretty-body (login-form))))
   (GET "/signup_form" req
        (html5 (pretty-head "Tully sign-up") (pretty-body
                                              [:h1 "Make a Tully Account"]
                                              (signup-form (:flash req)))))
   (GET "/logout" req
        (friend/logout* (resp/redirect (str (:context req) "/"))))
   (POST "/signup" {{:keys [username password confirm] :as params} :params :as req}
         (cond
           (not (= password confirm)) (redirect-with-flash req "/" (apply str "passwords " password " and " confirm " don't match!"))
           (db/user-exists (:db store) username) (redirect-with-flash req "/" (apply str "Username " username " already taken"))
           (str/blank? [username]) (redirect-with-flash req "/" "Username required!")
           (str/blank? [password]) (redirect-with-flash req "/" "Password required!")
           (str/blank? [password]) (redirect-with-flash req "/" "Confirmation password required!")
           :else (let [user (create-user (into {:db store} (select-keys params [:username :password])))]
             ;; push user into db
             (db/add-user store (:username user) (:password-hash user))
             (friend/merge-authentication
              (resp/redirect (context-uri req username))
              user))))
   ;; this uses compojure destructuring, https://github.com/weavejester/compojure/wiki/Destructuring-Syntax
   (GET "/main" req
        (friend/authenticated
         (let [username (:identity (friend/current-authentication))]
           (do
             (log/info "Main requested with req " req)
             (html5
              (pretty-head "Welcome")
              (pretty-body
               [:h2 (str "Tully Dashboard for " username)]
               [:p "Authenticated as " username]
               [:p "Return to the " (link-to (context-uri req "") " root")
                ", or " (link-to (context-uri req "logout") "log out") "."]))))))
   (GET "/cards" req
        (html5
         (pretty-head "DEVCARDS")
         [:body
          (include-js "js/devcards.js")]))
   (GET "/chsk" req ((:ring-ajax-get-or-ws-handshake (:sente system)) req))
   (POST "/chsk" req ((:ring-ajax-post (:sente system)) req))
   (route/resources "/")
   (route/not-found "PAGE NOT FOUND")))

(defn tully-credentials [store {:keys [username password] :as user}]
  (log/info "Called credentials fn with user " username)
  (if-let [user (db/get-verified-user (:db store) username password)]
    (do (log/info "Found and verified username " (:name user))
        (workflows/make-auth {:identity username}))))

(defn secure-routes [{store :store}]
  (friend/authenticate
   (main-routes {:store store})
   {:allow-anon? true
    :login-uri "/login"
    :default-landing-uri "/main"
    :unauthorized-handler #(-> (html5
                                [:h2 "You do not have sufficient privileges to access " (:uri %)])
                               resp/response
                               (resp/status 401))
    :credential-fn (partial tully-credentials store)
    :workflows [(workflows/interactive-form)]}))


;; now the Sente event handlers; see
;; https://github.com/danielsz/sente-system/blob/master/src/clj/example/my_app.clj
;; for a better idea of how these fit together.
(defmulti event-msg-handler :id)
(defn event-msg-handler* [{:as event-msg :keys [id ?data event]}]
  (log/debugf "Event: %s" event)
  (event-msg-handler event-msg))

(defmethod event-msg-handler :default
  [{:as event-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (log/debugf "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:unmatched-event-as-echoed-from-from-server event}))))

(defmethod event-msg-handler :db/get-objectid
  [{:as event-msg :keys [event ?reply-fn]}]
  (let [return-id  (ObjectId.)]
    (log/debugf "Requested new object ID, returning %s" return-id)
    (?reply-fn return-id)))

(defmethod event-msg-handler :db/get-user-sets
  [{:as event-msg :keys [event ?reply-fn ?data]}]
  (let [{:keys [user-id]} ?data
        sets (db/get-user-sets-as-map (get-in system [:store :db]) user-id)]
    (log/info "Sending sets " sets)
    (?reply-fn sets)))

(defmethod event-msg-handler :db/reset-test-database
  [{:as event-msg :keys [event ?reply-fn ?data]}]
  (db/make-test-data (get-in system [:store :db])))

(defmethod event-msg-handler :db/get-title-for-doi
  [{:as event-msg :keys [event ?reply-fn ?data]}]
  (do
    (log/info "Received get-title-for-doi request with data" ?data)
    (?reply-fn (crossref/sync-title (:doi ?data)))))
