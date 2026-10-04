(ns istu.archive.fetch
  "Обёртка для Fetch API."
  (:refer-clojure :exclude [get])
  (:require [cognitect.transit :as transit]
            [clojure.string :as str]
            [promesa.core :as p]))

(defn wrap-content-type [handler]
  (fn [{:keys [body content-type] :as opts}]
    (if (and body content-type)
      (handler (-> opts
                   (dissoc :content-type)
                   (assoc-in [:headers "content-type"] content-type)))
      (handler opts))))

(defn wrap-accept [handler]
  (fn [{:keys [accept] :as opts}]
    (handler (-> opts
                 (dissoc :accept)
                 (assoc-in [:headers "accept"] accept)))))

(defn wrap-query-params [handler]
  (fn [{:keys [url query-params] :as opts}]
    (if query-params
      (let [params (js/URLSearchParams. (clj->js query-params))
            url    (str url "?" (.toString params))]
        (handler (-> opts
                     (dissoc :query-params)
                     (assoc :url url))))
      (handler opts))))

(defn- request*
  [{:keys [url method headers body] :or {method :get}}]
  {:pre [(not (nil? url))]}
  (let [req {:method  (str/upper-case (name method))
             :headers headers
             :body    body}]
    (-> (js/fetch url (clj->js req))
        (p/then (fn [res]
                  {:ok?         (.-ok res)
                   :status      (.-status res)
                   :headers     (into {} (map js->clj) (.-headers res))
                   :body        (.-body res)
                   :js/response res})))))

(def
  ^{:doc
    "Отправляет запрос с помощью Fetch API.

    Принимает в качестве единственного аргумента таблицу со следующими
    параметрами:

    :url           - URL ресурса (обязательный)
    :method        - метод запроса в виде ключевого слова (по умолчанию :get)
    :headers       - таблица заголовков и их значений
    :body          - тело запроса
    :accept        - MIME-тип тела ответа (заголовок Accept)
    :content-type  - MIME-тип тела запроса (заголовок Content-Type)"}
  request
  (-> request*
      wrap-accept
      wrap-content-type
      wrap-query-params))

(defn get
  [url & [opts]]
  (request (assoc opts :url url :method :get)))

(defn post
  [url & [opts]]
  (request (assoc opts :url url :method :post)))

(defn text-body
  [{:keys [^js/Response js/response]}]
  (.text response))

(defn json-body
  [{:keys [^js/Response js/response]} & {:keys [keywordize-keys]}]
  (p/then' (.json response)
           #(js->clj % :keywordize-keys keywordize-keys)))

(defn transit-body [res]
  (let [r (transit/reader :json)]
    (p/then' (text-body res) #(transit/read r %))))
