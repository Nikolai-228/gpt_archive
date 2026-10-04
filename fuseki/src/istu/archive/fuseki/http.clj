(ns istu.archive.fuseki.http
  "Вспомогательный модуль для работы с запросами в Fuseki"
  (:require [clojure.spec.alpha :as s]
            [ring.util.jakarta.servlet :as ring])
  (:import [java.util Locale]
           [org.apache.jena.fuseki.servlets
            ActionService
            HttpAction
            ServletOps]))

(s/def ::valid-action-method
  #{:delete :get :head :options :patch :post :put :trace})
(s/def ::action-map (s/map-of ::valid-action-method fn?))

(defn http-action
  "Создаёт обработчик операций Fuseki, работающий по протоколу Ring. Помимо
  обычных полей, таблица запроса также включает следующие ключи:

  :jena/dataset - набор данных, к которому производится запрос
  :jena/context - текущий контекст"
  [handlers & [middleware]]
  {:pre [(s/valid? ::action-map handlers)]}
  (proxy [ActionService] []
    (execAny [^String method ^HttpAction action]
      (let [method* (-> method (.toLowerCase Locale/ROOT) (keyword))]
        (when-not (get handlers method*)
          (ServletOps/errorMethodNotAllowed method)))
      (.execute this action))
    (validate [^HttpAction action] nil)
    (execute [^HttpAction action]
      (let [req (-> (.getRequest action)
                    (ring/build-request-map)
                    (assoc :jena/dataset (.getDataset action))
                    (assoc :jena/context (.getContext action)))
            handler  (get handlers (:request-method req))
            handler' (reduce (fn [h [f & args]]
                               (apply f h args))
                             handler middleware)
            res      (handler' req)]
        (ring/update-servlet-response (.getResponse action) res)))))
