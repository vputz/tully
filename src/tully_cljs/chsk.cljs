(ns tully-cljs.chsk
  (:require [taoensso.timbre :as log]
            [taoensso.sente :as sente])) ;; channel-socket Sente stuff from
;; https://github.com/danielsz/sente-system/blob/master/src/cljs/example/my_app.cljs


(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
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
