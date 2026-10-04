(ns istu.archive.services.triplestore
  (:require [cognitect.transit :as transit]
            [istu.archive.config :refer [env]]
            [istu.archive.fetch :as fetch]
            [istu.archive.rdf.ns :as ns]
            [promesa.core :as p]))

(def ^:private +sparql-prefixes+ (ns/sparql-prefixes))

(defn raw-query [opts]
  (-> (fetch/request (merge {:url (:sparql-endpoint @env)} opts))
      (p/catch #(p/rejected
                 (ex-info "Could not fetch the SPARQL endpoint"
                          {:type ::http-error} %)))
      (p/then (fn [{:keys [ok? status] :as res}]
                (if-not ok?
                  (p/then (fetch/text-body res)
                          #(p/rejected
                            (ex-info % {:type ::sparql-error
                                        :http-status status})))
                  res)))))

(defn get-bindings [query]
  (p/then (raw-query {:method       :post
                      :body         (str +sparql-prefixes+ query)
                      :content-type "application/sparql-query"
                      :accept       "application/sparql-results+json"})
          #(fetch/json-body % :keywordize-keys true)))

(defn get-forest [node-id]
  (p/then (raw-query {:method       :get
                      :url          (:forest-endpoint @env)
                      :query-params {"id" node-id}
                      :accept       "application/transit+json"})
          fetch/transit-body))

(defn forest-action [action]
  (let [writer (transit/writer :json)]
    (p/then (raw-query {:method       :post
                        :url          (:forest-endpoint @env)
                        :body         (transit/write writer action)
                        :content-type "application/transit+json"
                        :accept       "application/transit+json"})
            fetch/transit-body)))
