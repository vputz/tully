(ns tully.handler
  (:require [cemerick.friend :as friend]
            [cemerick.friend
             [credentials :as creds]
             [workflows :as workflows]]
            [clojure.string :as str]
            [compojure
             [core :refer [GET POST routes]]
             [route :as route]]
            [hiccup
             [element :refer [link-to]]
             [page :refer [html5 include-css include-js]]]
            [ring.util
             [anti-forgery :refer [anti-forgery-field]]
             [response :as resp]]
            [system.repl :refer [system]]
            [taoensso.timbre :as log]
            [tully
             [crossref :as crossref]
             [db :as db]
             [influx :as influx]])
  (:import java.net.URI
           org.bson.types.ObjectId))

;; pretty-head and pretty-body from cemerick's friend_demo
;; https://github.com/cemerick/friend-demo/blob/master/src/clj/cemerick/friend_demo/misc.cl

(defn pretty-head [title]
  [:head
   (include-css "css/normalize.css")
   (include-css "css/foundation.min.css")
   (include-css "css/tully.css")
   [:style {:type "text/css"} "ul {padding-left: 2em }"]
   (include-js "js/vendor/d3legend.js")
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
   [:div {:class "columns small-12 large-12"}
    [:h3 "Sign Up"]
    [:div {:class "row"}
     [:form {:method "POST" :name "signup-form" :action "signup" :class "columns small-8 large-8"}
      (anti-forgery-field)
      [:div "Username" [:input {:type "text" :name "username" :required "required"}]]
      [:div "Password" [:input {:type "password" :name "password" :required "required"}]]
      [:div "Confirm Password" [:input {:type "password" :name "confirm" :required "required"}]]
      [:div
       [:input {:type "submit" :name "submit-button" :class "button" :value "Sign up"}]
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
  {:identity username :password (creds/hash-bcrypt password)})

(defn- redirect-with-flash [req url flash]
  (assoc (resp/redirect (str (:context req) url)) :flash flash))

(defn main-routes [{store :store}]
  (routes 
   (GET "/" req
        (html5 (pretty-head "Tully")
               (pretty-body
                [:h1 "Welcome to Tully"]
                [:p (if-let [identity (friend/identity req)]
                      (clojure.string/join "Logged in")
                      [:span  (link-to (context-uri req "signup_form") "Sign up") " to make an account, or log in below!"])]
                (login-form))))
   (GET "/login" req
        (html5 (pretty-head "Tully login") (pretty-body (login-form))))
   (GET "/signup_form" req
        (html5 (pretty-head "Tully sign-up") (pretty-body
                                              [:h1 "Make a Tully Account"]
                                              (signup-form (:flash req)))))
   (GET "/logout" req
        (friend/logout* (resp/redirect (str (:context req) "/"))))
   (POST "/signup" {{:keys [username password confirm] :as params} :params :as req}
         (letfn [(redir [flash] (redirect-with-flash req "/signup_form" flash))]
           (cond
             (not= password confirm) (redir [:div "Passwords " password " and " confirm " don't match!"])
             (db/user-exists (:db store) username) (redir [:div "Username " username " already taken"])
             (str/blank? username) (redir [:div "Username required!"])
             (str/blank? password) (redir [:div "Password required!"])
             (str/blank? confirm) (redir [:div "Confirmation password required!"])
             :else (let [user (create-user (into {:db store} (select-keys params [:username :password])))]
                     (do 
                       (log/debug {:event "create-user" :data user})
                       (db/add-user (:db store) (:identity user) (:password user))
                       (friend/merge-authentication
                        (resp/redirect (context-uri req "/main"))
                        user))))))
   ;; this uses compojure destructuring, https://github.com/weavejester/compojure/wiki/Destructuring-Syntax
   (GET "/main" req
        (friend/authenticated
         (let [username (:identity (friend/current-authentication))]
           (log/debug {:event "main-requested"})
           (html5
            (pretty-head (str/join ["Tully - welcome, " username]))
            [:div {:style {:display "hidden"}
                   :id "server-data"
                   :username username}]
            [:div {:id "app"}]
            (include-js "js/main.js")))))
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
  (log/debug {:event "credentials-fn" :data username})
  (if-let [user (db/get-verified-user (:db store) username password)]
    (do (log/debug {:event "found-username" :data (:name user)})
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
  (log/debug {:event "msg-handler" :data event})
  (event-msg-handler event-msg))



(defmethod event-msg-handler :default
  [{:as event-msg :keys [event id ?data uid ring-req ?reply-fn send-fn client-id]}]
  (let [session (:session ring-req)
        user-id (:uid session)]
    (log/debug {:event "unhandled-event" :data {:event event :uid uid :client-id client-id}})
    (when ?reply-fn
      (?reply-fn {:unmatched-event-as-echoed-from-from-server event}))))

(defmethod event-msg-handler :chsk/ws-ping
  [{:as event-msg :keys [event ?data]}]
  (log/debug {:event "ws-ping" :data ?data}))

(defmethod event-msg-handler :db/get-objectid
  [{:as event-msg :keys [event ?reply-fn]}]
  (let [return-id  (ObjectId.)]
    (log/debug {:event "request-new-oid" :data return-id})
    (?reply-fn {:objectid return-id})))

(defmethod event-msg-handler :db/get-user-sets
  [{:as event-msg :keys [event ?reply-fn ?data]}]
  (let [{:keys [user-id]} ?data
        sets (db/get-user-sets-as-map (get-in system [:store :db]) user-id)]
    (log/debug {:event "sending-sets" :data sets})
    (?reply-fn sets)))

(defmethod event-msg-handler :db/reset-test-database
  [{:as event-msg :keys [event ?reply-fn ?data]}]
  (db/make-test-data (get-in system [:store :db])))

(defmethod event-msg-handler :db/get-title-for-doi
  [{:as event-msg :keys [event ?reply-fn ?data]}]
  (do
    (log/debug {:event "recv-get-title-doi-req" :data ?data})
    (?reply-fn (crossref/sync-title (:doi ?data)))))



(defn group-to-metric
  [client group days-period days-interval]
  (let [dois (map :doi (vals (:papers group)))
        metrics (influx/group-metrics client dois days-period days-interval)]
    (assoc (dissoc group :papers) :metrics metrics)))

(defn group-metrics-as-map
  "Gets the metrics for the given group-ids in the form expected for the front-end db"
  [client groups days-period days-interval]
  (reduce-kv (fn [result k v]
               (assoc result k
                      (group-to-metric client v days-period days-interval)))
             {}
             groups))


(defn send-groups [uid]
  (log/debug {:event "sending-groups" :data uid})
  (let [groups (db/get-user-sets-as-map (get-in system [:store :db]) uid)
        group-metrics (group-metrics-as-map (get-in system [:influx :client]) groups 30 1)
        chsk-send! (get-in system [:sente :chsk-send!])]
    (chsk-send! uid [:db/groups-from-db {:groups groups
                                         :group-metrics group-metrics}])))

(defmethod event-msg-handler :db/request-user-sets
  [{:as event-msg :keys [event uid]}]
  (do 
    (log/debug {:event "sending-user-stats" :data {:requesting-uid uid}})
    (send-groups uid)))


(defmethod event-msg-handler :db/write-user-groups-to-db
  [{:as event-msg :keys [event ?reply-fn ?data uid]}]
  (let [sets (map db/set-papers-map-to-set-papers-seq (vals (:groups ?data)))]
    (log/debug {:event "writing-user-groups" :data sets})
    (db/update-user-sets (get-in system [:store :db]) sets uid)
    (send-groups uid)))

(defmethod event-msg-handler :db/write-new-paper-to-db
  [{:as event-msg :keys [event ?reply-fn ?data uid]}]
  (let [{:keys [group-id paper-doi paper-title]} ?data]
    (log/debug {:event "write-new-paper-to-db" :data ?data})
    (db/write-paper-to-group (get-in system [:store :db]) group-id paper-doi paper-title)
    (send-groups uid)
    ))

(defmethod event-msg-handler :db/delete-group-id-from-db
  [{:keys [event ?data uid]}]
  (let [{:keys [group-id]} ?data]
    (log/debug {:event "receive-delete-request" :data group-id})
    (db/delete-group-id (get-in system [:store :db]) group-id)))

(defmethod event-msg-handler :db/create-new-group-named
  [{:keys [event ?data uid]}]
  (let [{:keys [group-name]} ?data]
    (log/debug {:event "receive-create-request" :data group-name})
    (db/create-group-for-user (get-in system [:store :db]) uid group-name)
    (send-groups uid)))
