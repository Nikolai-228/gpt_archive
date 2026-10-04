(ns istu.archive.server
  (:require [istu.archive.config :refer [env]]
            [istu.archive.middleware :refer [wrap-log-requests
                                             wrap-measure-time]]
            [istu.archive.routes :refer [routes]]
            [istu.archive.routes.core :refer [get-route-url]]
            [istu.archive.routes.login :as-alias login]
            [istu.archive.session :as session]
            [macchiato.middleware.defaults :refer [wrap-defaults]]
            [macchiato.server :as http]
            [macchiato.util.response :as res]
            [mount.core :as mount]
            [reitit.core :as r]
            [reitit.ring :as ring]
            [taoensso.telemere :as t]))

(defn not-found [req res raise]
  (-> (res/not-found "Not found.")
      (res/content-type "text/plain")
      res))

(defn unauthorized
  [{:keys [url], ::r/keys [router], :as req} res raise]
  (let [path (get-route-url router ::login/login
                            :query-params {:redirect url})]
    (-> (res/see-other path)
        (assoc :flash
               {:http-status 400
                :message     "Для выполнения этого действия Вы должны войти в систему."})
        res)))

(def app
  (ring/ring-handler
   (ring/router
    routes
    {:data
     {:middleware [[session/wrap-auth {:on-unauthorized #'unauthorized}]
                   [session/wrap-role]]
      :auth       (boolean (:archive-disallow-unauthorized @env))}})
   not-found
   {:middleware [[wrap-log-requests]
                 [wrap-measure-time]
                 [wrap-defaults
                  {:responses {:content-types   true
                               :default-charset "utf-8"}
                   :session   {:cookie-name "sessionId"
                               :flash       true
                               :store       (session/secure-session-store)}
                   :static    {:resources "static"}}]
                 [session/wrap-session-expiration
                  {:session-age (* 1000 60 60 24 7)}]
                 [session/wrap-user-data]]}))

(def server (atom nil))

(defn ^:dev/after-load-async start [& [done]]
  (mount/start)
  (let [port (or (some-> @env :port js/parseInt) 8000)
        cb   (fn []
               (when done (done))
               (t/log! :info ["Listening on port" port]))]
    (if-not @server
      (reset! server (http/start {:port       port
                                  :handler    app
                                  :on-success cb}))
      (throw (js/Error. "Server already running.")))))

(defn ^:dev/before-load-async stop [done]
  (mount/stop)
  (let [cb (fn []
             (t/log! :info ["Server stopped"])
             (when done (done)))]
    (if @server
      (swap! server #(do (.close ^js % cb) nil))
      (done))))

(defn main [& _args]
  (start))
