(ns istu.archive.session
  (:require [macchiato.middleware.session.store :refer [SessionStore]]
            [macchiato.util.response :as res]
            [nano-id.core :refer [nano-id]]
            [reitit.core :as r]))

(def generate-session-key nano-id)

;; Adapted from:
;; <https://github.com/macchiato-framework/macchiato-core/blob/master/src/macchiato/middleware/session/memory.cljs>
(deftype SecureSessionStore [session-map]
  SessionStore
  (read-session [_ key]
    (get @session-map key))
  (write-session [_ key data]
    (let [key (or key (generate-session-key))]
      (swap! session-map assoc key data)
      key))
  (delete-session [_ key]
    (swap! session-map dissoc key)
    nil))

(defn secure-session-store
  "Создаёт хранилище сессий внутри памяти. Идентификаторы сессий генерируются с
  помощью криптогарфически безопасного Nano-ID."
  ([] (secure-session-store (atom {})))
  ([session-atom] (SecureSessionStore. session-atom)))

(defn- expiration-date [session-age] (+ session-age (system-time)))
(defn- session-expired? [{::keys [expires]}] (< expires (system-time)))
(defn- should-renew-session? [{::keys [expires]} session-age]
  (< (- expires (system-time)) (* 0.5 session-age)))

(defn wrap-session-expiration
  "Промежуточное ПО, которое следит за сроком истечения сессии и при необходимости
  продляет сессию (выпуская новый идентификатор сесии) либо уничтожает сессию.

  Принимает следующие параметры:
  :session-age - срок действия сессии в миллисекундах

  Сессия продляется при истечении половины срока действия."
  [handler {:keys [session-age]}]
  (fn [{:keys [session] :as req} res raise]
    (if (seq session)
      (cond
        (session-expired? session)
        ;; Maybe propose the user to relog in
        (handler req #(res (assoc % :session nil)) raise)

        (should-renew-session? session session-age)
        (handler req
                 #(let [expires (expiration-date session-age)
                        session (-> (or (:session %) session)
                                    (assoc ::expires expires)
                                    (vary-meta assoc :recreate true))]
                    (res (-> %
                             (assoc :session session
                                    :session-cookie-attrs {:max-age session-age}))))
                 raise)

        :else (handler req res raise))
      (handler req
               #(if (:session %)
                  (let [expires (expiration-date session-age)]
                    (res (-> %
                             (assoc :session-cookie-attrs {:max-age session-age})
                             (assoc-in [:session ::expires] expires))))
                  (res %))
               raise))))

(defn wrap-user-data
  "Промежуточное ПО, которое получает пользователя на основе данных сессии."
  [handler]
  (fn [{:keys [session] :as req} res raise]
    (let [{:keys [user]} session
          req  (if user
                 (assoc req :user-data user)
                 req)
          res' (fn [response-map]
                 (if (contains? response-map :user-data)
                   (if-let [user-data (:user-data response-map)]
                     (res (-> response-map
                            (assoc :session (assoc session :user user-data))
                            (dissoc :user-data)))
                     (res (-> response-map
                              (assoc :session nil)
                              (dissoc :user-data))))
                   (res response-map)))]
      (handler req res' raise))))

(defn default-forbidden [req res raise]
  (-> (res/forbidden "Forbidden.")
      (res/content-type "text/plain")
      res))

(defn wrap-role
  "Промежуточное ПО, которое допускает пользователя к определённом маршрутам только
  при наличии у него определённых ролей. Имеет смысл предварительно обернуть
  обработчик в `wrap-auth`."
  [handler {:keys [on-forbidden], :or {on-forbidden default-forbidden}}]
  (fn [{:keys [user-data]
        {{{:keys [role]} :auth} :data} ::r/match
        :as req}
       res raise]
    (if role
      (if (contains? (:roles user-data) role)
        (handler req res raise)
        (on-forbidden req res raise))
      (handler req res raise))))

(defn wrap-auth
  "Промежуточное ПО, которое допускает пользователя к определённым маршрутам в
  зависимости от его авторизации в системе. Необходимо, чтобы до этого было обёрнуто
  в `wrap-user-data`."
  [handler {:keys [on-unauthorized]}]
  (fn [{:keys [user-data], {{:keys [auth]} :data} ::r/match, :as req} res raise]
    (if auth
      (if user-data
        (handler req res raise)
        (on-unauthorized req res raise))
      (handler req res raise))))
