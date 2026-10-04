(ns istu.archive.routes.login
  (:require [istu.archive.config :refer [env]]
            [istu.archive.macros.request :refer [with-request-data]]
            [istu.archive.services.postgres :as pg]
            [istu.archive.views.login :as login]
            [macchiato.middleware.params :refer [wrap-params]]
            [macchiato.util.response :as res]
            [promesa.core :as p]))

(defn wrap-bypass-login
  [_handler]
  (fn [req res raise]
    (-> (res/see-other "/")
        (assoc :user-data {:username "test"
                           :roles    #{:admin :editor}})
        res)))

(defn login [{:keys [request-method params flash] :as req} res raise]
  (case request-method
    :get
    (with-request-data req
      (if-let [{:keys [http-status message]} flash]
        (-> {:status http-status
             :body   (login/render-login {:error message})}
            (res/content-type "text/html")
            res)
        (-> (login/render-login {})
            (res/ok)
            (res/content-type "text/html")
            res)))

    :post
    (-> (p/let [{:strs [username password redirect]} params]
          (when (some empty? (list username password))
            (throw (ex-info "Укажите имя пользователя и пароль." {})))
          (p/let [user (pg/auth-user username password)]
            (if user
              (p/let [user-id (:user_id user)
                      roles   (pg/get-user-roles user-id)
                      user    (assoc user :roles roles)]
                (-> (res/see-other (or redirect "/"))
                    (assoc :user-data user)
                    res))
              (throw (ex-info "Неверное имя пользователя или пароль." {})))))
        (p/catch #(with-request-data req
                    (-> (login/render-login {:error (ex-message %)})
                        (res/bad-request)
                        (res/content-type "text/html")
                        res))))

    (res (res/method-not-allowed "Method not allowed."))))

(def routes {:handler    #'login
             :name       ::login
             :auth       false
             :middleware [[wrap-params]
                          (when (:archive-bypass-login @env) [wrap-bypass-login])]})
