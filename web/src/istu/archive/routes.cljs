(ns istu.archive.routes
  (:require [hiccups.runtime :as hiccupsrt]
            [istu.archive.fetch :as fetch]
            [istu.archive.macros.resource :refer [load-edn]]
            [istu.archive.macros.request :refer [with-request-data]]
            [istu.archive.middleware :refer [wrap-read-body]]
            [istu.archive.rdf.model :as rdfm]
            [istu.archive.rdf.ns :as ns]
            [istu.archive.routes.admin :as admin]
            [istu.archive.routes.entity :as entity]
            [istu.archive.routes.index :as index]
            [istu.archive.routes.new-item :as new-item]
            [istu.archive.routes.search :as search]
            [istu.archive.routes.sparql :as sparql]
            [istu.archive.routes.suggest :as suggest]
            [istu.archive.routes.upload :as upload]
            [istu.archive.routes.login :as login]
            [istu.archive.routes.logout :as logout]
            [istu.archive.services.triplestore :as ts]
            [istu.archive.views.statement-form :refer [render-editor]]
            [macchiato.middleware.params :refer [wrap-params]]
            [macchiato.util.response :as res]
            [promesa.core :as p]
            [taoensso.telemere :as t]
            ["node:fs" :as fs]
            ["gm" :as gm*]))

(def ^:private sparql-examples-edn (load-edn "./data/sparql_examples.edn"))
(defn sparql-examples [req res raise]
  (res {:status  200
        :headers {"Content-Type" "application/json"}
        :body    (-> sparql-examples-edn
                     clj->js
                     js/JSON.stringify)}))

(def ^:private +sparql-prefixes+ (ns/sparql-prefixes))

(defn sparql-query [{:keys [body headers]} res raise]
  (let [start (system-time)
        opts  {:method       :post
               :body         (str +sparql-prefixes+ "\n" body)
               :content-type (get headers "content-type")
               :accept       (get headers "accept")}]
    (-> (p/let [response (ts/raw-query opts)]
          (let [finish (system-time)]
            (p/then' (fetch/text-body response)
                     #(res {:status 200
                            :headers
                            {"SPARQl-Query-Time" (.toFixed (- finish start) 2)
                             "Content-Type" (get (:headers response) "content-type")}
                            :body %}))))
        (p/catch #(res {:status  (or (-> % ex-data :http-status) 500)
                        :headers {"Content-Type" "text/plain"}
                        :body    (ex-message %)})))))

(def ^:private ontology-file
  (try
    (fs/readFileSync "../ontology/istu.ttl" #js {:encoding "utf-8"})
    (catch :default ex
      (t/log! :error ["Could not find the ontology file" ex])
      nil)))

;; TODO: employ content negotiation sometime -M 2024.11.23
(defn ontology [req res raise]
  (if ontology-file
    (res {:status  200
          :headers {"Content-Type" "text/turtle"
                    "Content-Disposition" "inline"}
          :body    ontology-file})
    (res {:status  200
          :body    "Not found."})))

(defn- ontology-predicate-opts [iri]
  (p/let [results (ts/get-bindings
                   (str "SELECT ?range ?preferred ?suggest ?allowIri "
                        "FROM <urn:x-arq:DefaultGraph> "
                        "FROM <urn:x-arq:UnionGraph>"
                        "{"
                        "  VALUES ?pred { <" iri "> }"
                        "  {"
                        "    ?pred hint:preferredRange ?range ."
                        "    BIND(\"1\" AS ?preferred)"
                        "  }"
                        "  UNION"
                        "  {"
                        "    ?pred rdfs:range|schema:rangeIncludes ?range ."
                        "  }"
                        "  OPTIONAL {"
                        "    ?pred hint:showInitialSuggestions ?suggest"
                        "  }"
                        "  OPTIONAL {"
                        "    ?pred hint:allowArbitraryIRI ?allowIri"
                        "  }"
                        "}"
                        "ORDER BY DESC(?preferred)"))]
    (when-let [solution (-> results :results :bindings first)]
      {:rdf-range       (some-> solution :range :value rdfm/named-node-canonical)
       :initial-suggest (some-> solution :suggest :value parse-boolean)
       :allow-iri       (some-> solution :allowIri :value parse-boolean)})))

(defn- get-predicate-opts [iri]
  (p/let [{:keys [rdf-range] :as opts} (ontology-predicate-opts iri)
          kinds (cond
                  (#{:xsd/string :rdf/langString :schema/Text} rdf-range)
                  '(:text)

                  (= :time/TemporalEntity rdf-range)
                  '(:instant :interval)

                  (= :time/Instant rdf-range)
                  '(:instant)

                  (= :time/Interval rdf-range)
                  '(:interval)

                  ;; TODO: хардкод
                  (= :org/Membership rdf-range)
                  '(:tree)

                  :else '(:item))]
    (assoc opts :kinds kinds)))

(defn editor
  [{{:strs [predicate domain kind]} :query-params, :as req} res raise]
  (-> (p/let [pred-opts (when (seq predicate) (get-predicate-opts predicate))]
        (when (and kind (not (some #(= (keyword kind) %) (:kinds pred-opts))))
          (throw (ex-info "Invalid value kind."
                          {:predicate predicate
                           :options   pred-opts
                           :kind      kind})))
        (-> (with-request-data req
              (render-editor (assoc pred-opts
                                    :predicate predicate
                                    :domain    domain
                                    :kind      (keyword kind))))
            (hiccupsrt/render-html)
            (res/ok)
            (res/content-type "text/html")
            res))
      (p/catch raise)))

(def gm (gm*/subClass #js {:imageMagick true}))

;; TODO: temporary solution -M 2024.12.16
(defn image-convert [{{:strs [id]} :query-params} res raise]
  (if (empty? id)
    (res (res/bad-request))
    (-> (p/let [path (str "./static/images/" id ".jpg")
                body (-> ^js (gm path)
                         (.resize nil 1024 ">")
                         (.noProfile)
                         (.stream "jpeg"))]
          (-> (res/ok body)
              (res/content-type "image/jpeg")
              (res/header "Cache-Control" "max-age=604800")
              res))
        (p/catch raise))))

(def routes
  [["/" index/routes]
   ["/ns" ontology]
   ["/search" search/routes]
   ["/object/:id" entity/routes]
   ["/new-item" new-item/routes]
   ["/upload" upload/routes]
   ["/login" login/routes]
   ["/logout" logout/routes]
   ["/sparql" sparql/routes]
   ["/admin" admin/routes]
   ["/image-convert" {:get image-convert
                      :middleware [[wrap-params]]}]
   ["/examples.json" {:get sparql-examples}]
   ["/api"
    ["/editor" {:get editor, :middleware [[wrap-params]]}]
    ["/sparql" {:post sparql-query, :middleware [[wrap-read-body]]}]
    ["/suggest" suggest/routes]]])
