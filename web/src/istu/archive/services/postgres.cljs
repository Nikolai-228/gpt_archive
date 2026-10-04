(ns istu.archive.services.postgres
  (:require [mount.core :refer [defstate]]
            [taoensso.telemere :as t]
            [promesa.core :as p]
            ["bcrypt" :as bcrypt]
            ["pg" :as pg]))

(defstate ^{:on-reload :noop} pool
  :start (do
           (t/log! :info ["Creating the PostgreSQL pool"])
           (pg/Pool.)))

;; Modelled after pg2: https://github.com/igrishaev/pg2/blob/master/docs/folders.md
(defn folder-default
  ([]         (transient []))
  ([acc!]     (persistent! acc!))
  ([acc! row] (conj! acc! row)))

(defn first-row
  ([]         (atom nil))
  ([acc!]     @acc!)
  ([acc! row] (when (nil? @acc!)
                (reset! acc! row))))

(defn kv [k v]
  {:pre [(keyword? k) (keyword? v)]}
  (fn
    ([]         (transient {}))
    ([acc!]     (persistent! acc!))
    ([acc! row] (assoc! acc! (k row) (v row)))))

(defn fold-reduce [f x]
  (fn
    ([] (atom x))
    ([acc!] @acc!)
    ([acc! row] (swap! acc! f row))))

(defn execute
  "Выполняет указанный запрос и возвращает вектор возвращённых строчек,
  представленных в виде хеш-таблиц."
  [conn query-string & [{:keys [as params name], :or {as folder-default}}]]
  (-> (p/let [query  {:text   query-string
                      :values params
                      :name   name}
              result (.query conn (clj->js query))
              acc!   (as)]
        (doseq [row (.-rows result)]
          (as acc! (js->clj row :keywordize-keys true)))
        (as acc!))
      (p/catch #(p/rejected (ex-info "SQL query failed." {:type ::sql-error} %)))))

(defn find-user-by-username [username]
  (execute @pool
           "SELECT * FROM users WHERE username = $1"
           {:params [username] :as first-row}))

(defn get-user-roles [user-id]
  (execute @pool
           "SELECT role_name
              FROM user_roles NATURAL JOIN roles
             WHERE user_id = $1"
           {:params [user-id]
            :as (fold-reduce (fn [acc {:keys [role_name]}]
                               (conj acc (keyword role_name)))
                             #{})}))

(def salt-rounds 10)

(defn register-user [data]
  (p/let [{:keys [username given-name family-name password]} data
          password-hash (bcrypt/hash password salt-rounds)]
    (execute @pool
             "INSERT INTO users (username, given_name, family_name, password_hash)
              VALUES ($1, $2, $3, $4)"
             {:params [username given-name family-name password-hash]
              :name   "register-user"})))

(defn auth-user [username password]
  ;; TODO: possible time attack? -M 2024.10.21
  (p/let [{:keys [password_hash] :as user} (find-user-by-username username)]
    (when user
      (p/let [matches? (bcrypt/compare password password_hash)]
        (when matches? user)))))

(def ^:private ui-messages (atom {}))
(defn- init-ui-messages []
  (-> (execute @pool "SELECT * FROM ui_messages"
               {:as (kv :message_id :message_content)})
      (p/then' #(reset! ui-messages %))
      (p/catch (fn [_]))))
(init-ui-messages)

(defn ui-messages-names [] (keys @ui-messages))
(defn get-ui-message [id] (get @ui-messages id id))
(defn update-ui-message [id content]
  (p/do
    (execute @pool
             "UPDATE ui_messages
                 SET message_content = $2
               WHERE message_id = $1"
             {:params [id content]})
    (swap! ui-messages assoc id content)))
