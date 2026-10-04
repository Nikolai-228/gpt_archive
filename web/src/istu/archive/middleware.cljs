(ns istu.archive.middleware
  (:require [clojure.string :as str]
            [macchiato.util.request :as req]
            [taoensso.telemere :as t]
            ["concat-stream" :as concat-stream]
            ["multiparty" :as multiparty]
            ["stream" :refer [Readable]]
            ["qs" :as qs]))

(defn wrap-measure-time
  [handler]
  (fn [req res raise]
    (let [start (system-time)
          res'  #(let [finish (system-time)
                       time   (- finish start)]
                   (-> %
                       (assoc ::response-time time)
                       (res)))]
      (handler req res' raise))))

(defn- log-request! [req res]
  (t/log! :info [(-> req :request-method name str/upper-case)
                 (-> req :uri)
                 (-> res :status)
                 (some-> res ::response-time (.toFixed 2) (str "ms"))]))

(defn wrap-log-requests
  [handler]
  (fn [req res raise]
    (let [res' #(do
                  (log-request! req %)
                  (res %))]
      (handler req res' raise))))

(defn wrap-read-body
  [handle]
  (fn [{:keys [^Readable body] :as req} res raise]
    (if body
      (let [cs (concat-stream
                #(handle (assoc req :body %) res raise))]
        (.on body "error" raise)
        (.pipe body cs))
      (handle req res raise))))

(defn urlencoded? [req]
  (= "application/x-www-form-urlencoded" (req/content-type req)))
(defn multipart? [req]
  (= "multipart/form-data" (req/content-type req)))

(defn- parse-query-entries [input]
  (->> input
       (.parseEntries qs)
       (map #(MapEntry. (.-key %) (.-value %) nil))))

(defn wrap-query-entries
  "Промежуточное ПО, которое разбирает параметры запроса в последовательность
  пар [ключ значение]. Результат записывается в поле :query-entries запроса."
  [handler]
  (fn [{:keys [query-string] :as req} res raise]
    (if (string? query-string)
      (let [entries (parse-query-entries query-string)]
        (handler (assoc req :query-entries entries) res raise))
      (handler req res raise))))

(defn wrap-form-entries
  "Промежуточное ПО, которое разбирает данные формы (в формате
  x-www-form-urlencoded) в последовательность пар [ключ значение]. Результат
  записывается в поле :body запроса."
  [handler]
  (wrap-read-body
   (fn [{:keys [body] :as req} res raise]
     (if (urlencoded? req)
       (let [entries (parse-query-entries (str body))]
         (handler (assoc req :body entries) res raise))
       (handler req res raise)))))

(defn wrap-multipart-entries
  "Промежуточное ПО, которое разбирает данные формы (в формате
  multipart/form-data). Текстовые поля записываются в :body запроса как
  последовательность пар [ключ значение]. Аналогично, в :files записываются
  файлы."
  [handler]
  (fn [{:keys [body] :as req} res raise]
    (if (multipart? req)
      (let [form   (multiparty/Form.)
            fields (transient [])
            files  (transient [])]
        (.on form "error" raise)
        (.on form "field" #(conj! fields [%1 %2]))
        (.on form "file"  #(conj! files [%1 (js->clj %2 :keywordize-keys true)]))
        (.on form "close" #(handler (assoc req
                                           :body  (persistent! fields)
                                           :files (persistent! files))
                                    res raise))
        (.parse form body))
      (handler req res raise))))
