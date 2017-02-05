(ns tully-cljs.chsk
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [taoensso.timbre :as log]
            [taoensso.sente :as sente]
            [taoensso.encore :as enc]
            [clojure.string :as str]
            [cljs.core.async :refer [chan <! put!]]
            [cognitect.transit :as transit]
            [goog.array :as garray]
            [re-frame.core :refer [dispatch]]
            [taoensso.sente.packers.transit :as sente-transit])) ;; channel-socket Sente stuff from
;; https://github.com/danielsz/sente-system/blob/master/src/cljs/example/my_app.cljs


(defrecord ObjectId [stringrep]
  IComparable
  (-compare [_ other]
    (garray/defaultCompare stringrep (.-stringrep other))))

(def objectid-writer
  (transit/write-handler
   (constantly "object-id")
   (fn [o] (.-stringrep o))
   (fn [o] (.-stringrep o))))

(def objectid-reader
  (transit/read-handler
   (fn [from-str] (ObjectId. from-str))))

(defn make-chsk-sockets [user-id]
  (let [client-part (enc/uuid-str)
        client-id (str/join "-" [user-id client-part])
        {:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket-client!
         "/chsk"
         {:type :auto
          :client-id client-id
          :packer (sente-transit/get-transit-packer
                   :json
                   {:handlers {ObjectId objectid-writer}}
                   {:handlers {"object-id" objectid-reader}})})]
    (log/info "MAKING SOCKET with client-id " client-id " using client-part " client-part)
    (def chsk chsk)
    (def ch-chsk ch-recv) ; channel-socket receive
    (def ch-send! send-fn)
    (def chsk-state state)
    ))

(defn wait-for-msg
  [ch]
  (go
    (let [val (<! ch)]
      (log/info "Waited; msg= " val))))

(defmulti event-msg-handler :id) ;; dispatch on event-id

(defmulti chsk-event-handler first)

(defmethod chsk-event-handler :db/groups-from-db
  [[id data]]
  (log/debugf "chsk-event-handler %s %s" id data)
  (dispatch [:set-user-sets-from-db (:groups data)])
  (dispatch [:set-user-metrics-from-db (:group-metrics data)]))

(defmethod chsk-event-handler :chsk/ws-ping
  []
  (log/debug "ping"))

(defn event-msg-handler* [{:as event-msg :keys [id ?data event]}]
  (log/debugf "Event: %s with id %s" event id)
  (event-msg-handler event-msg))

(defmethod event-msg-handler :default
  [{:as event-msg :keys [event]}]
  (log/debugf "Unhandled event: %s" event))

(defmethod event-msg-handler :chsk/ws-ping
  [{:as event-msg :keys [event ?data]}]
  (log/debug {:event "ping"}))

(defmethod event-msg-handler :chsk/recv
  [{:as event-msg :keys [event ?data]}]
  (log/debugf "Handled chsk/recv with event %s and data %s" event ?data)
  (chsk-event-handler ?data))

(defmethod event-msg-handler :chsk/state
  [{:as event-msg :keys [event ?data]}]
  (let [[init-state new-state] ?data]
    (log/debugf "Handled chsk/state with new state %s" new-state)
    (when (:first-open? new-state)
      (dispatch [:request-user-sets-from-db]))))


(def router_ (atom nil))

(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))

(defn start-router! []
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))

;; functions using callbacks

(defn get-server-objectid []
  (let [result-chan (chan)
        result (atom nil)]
    (ch-send! [:db/get-objectid] 1000
              (fn [cb-reply] (log/debugf "Callback oid reply: %s" cb-reply)
                (if (sente/cb-success? cb-reply)
                  (put! result-chan (:objectid cb-reply))
                  (put! result-chan nil))))
    (go
      (reset! result (<! result-chan)))
    @result))

(defn request-and-set-user-sets-from-db []
  (ch-send! [:db/request-user-sets])
  ;; (ch-send! [:db/get-user-sets {:user-id "vputz"}] 1000
  ;;           (fn [cb-reply]
  ;;             (log/debugf "Callback get-user-sets: %s" cb-reply)
  ;;             (if (sente/cb-success? cb-reply)
  ;;               (dispatch [:set-user-sets-from-db cb-reply])
  ;;               (log/info "Error in user set reply"))))
  )

(defn reset-test-database []
  (ch-send! [:db/reset-test-database {:user-id "vputz"}] 1000))

(defn get-title-for-doi [doi component-state]
  (ch-send! [:db/get-title-for-doi {:doi doi}] 1000
            (fn [cb-reply]
              (log/debugf "Callback get-title-for-doi: %s" cb-reply)
              (if (sente/cb-success? cb-reply)
                (do
                  (swap! component-state
                         merge {:new-paper-title (:title cb-reply)
                                :valid-lookup (:valid-lookup cb-reply)}))
                (log/debugf "Error on chsk doi lookup")))))

(defn write-new-paper-to-db [group-id paper-doi paper-title]
  (ch-send! [:db/write-new-paper-to-db {:group-id group-id
                                        :paper-doi paper-doi
                                        :paper-title paper-title}]))

(defn write-user-groups-to-db [groups]
  (ch-send! [:db/write-user-groups-to-db {:groups groups}]))

(defn delete-group-id-from-db [group-id]
  (ch-send! [:db/delete-group-id-from-db {:group-id group-id}]))

(defn create-new-group-in-db [group-name]
  (ch-send! [:db/create-new-group-named {:group-name group-name}]))
