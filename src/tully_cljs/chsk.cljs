(ns tully-cljs.chsk
  (:require [taoensso.timbre :as log]
            [taoensso.sente :as sente]
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
   "object-id"
   (fn [o] (.-stringrep o))
   (fn [o] (.-stringrep o))))

(def objectid-reader
  (transit/read-handler
   (fn [from-str] (ObjectId. from-str))))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
       "/chsk"
       {:type :auto
        :packer (sente-transit/get-transit-packer
                 :json
                 {:handlers {ObjectId objectid-writer}}
                 {:handlers {"object-id" objectid-reader}})})]
  (def chsk chsk)
  (def ch-chsk ch-recv) ; channel-socket receive
  (def ch-send! send-fn)
  (def chsk-state state)
  )

(defmulti event-msg-handler :id) ;; dispatch on event-id

(defn event-msg-handler* [{:as event-msg :keys [id ?data event]}]
  (log/debugf "Event: %s" event)
  (event-msg-handler event-msg))

(defmethod event-msg-handler :default
  [{:as event-msg :keys [event]}]
  (log/debugf "Unhandled event: %s" event))

(def router_ (atom nil))

(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))

(defn start-router! []
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))

;; functions using callbacks

(defn get-server-objectid []
  (ch-send! [:db/get-objectid] 1000
            (fn [cb-reply] (log/debugf "Callback oid reply: %s" cb-reply))))

(defn set-user-sets-from-db []
  (ch-send! [:db/get-user-sets {:user-id "vputz"}] 1000
            (fn [cb-reply]
              (log/debugf "Callback get-user-sets: %s" cb-reply)
              (if (sente/cb-success? cb-reply)
                (dispatch [:set-user-sets-from-db cb-reply])
                (log/info "Error in user set reply")))))
