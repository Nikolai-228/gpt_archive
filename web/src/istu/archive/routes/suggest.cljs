(ns istu.archive.routes.suggest
  (:require [clojure.string :as str]
            [istu.archive.macros.request :refer [with-request-data]]
            [istu.archive.rdf.ns :as ns]
            [istu.archive.routes.core :refer [get-route-url]]
            [istu.archive.routes.new-item :as-alias new-item]
            [istu.archive.services.triplestore :as ts]
            [istu.archive.views.suggest :refer [render-create-new-item
                                                render-suggestions]]
            [macchiato.middleware.params :refer [wrap-params]]
            [macchiato.util.response :as res]
            [promesa.core :as p]
            [reitit.core :as r]))

(defn- escape-word [word]
  (-> word
      (str/replace #"\"" "\\\\\\\"")
      (str/replace #":" "\\\\:")
      (str/replace #"\(" "\\\\(")
      (str/replace #"\)" "\\\\)")
      (str/replace #"/" "\\\\/")))

(defn- text-query-line [query]
  (let [parts (str/split query #":")]
    (case (count parts)
      2 ; uri
      (let [[prefix local] parts
            full (if (#{"http" "https"} prefix)
                   query
                   (if-let [iri (ns/kwi->iri (keyword prefix local))]
                     iri
                     (throw (ex-info (str "Unknown prefix: " prefix) {}))))
            escaped (escape-word full)]
        (str "(?entity ?score ?match_)"
             "text:query"
             "(schema:url \"" escaped "*\") ."))

      (let [words  (str/split query #"\s+")
            query' (->> words
                        (map #'escape-word)
                        (str/join " AND "))]
        (when (seq query')
          (str "(?entity ?score ?match_)"
               "text:query"
               "(schema:name \"(" query' "*)\") ."))))))

(defn- make-query [{:keys [target limit query] :as opts :or {limit 5}}]
  (let [validity (case target
                   :predicate
                   (when-let [rdf-type (:rdf-type opts)]
                     (str "?entity "
                          "(rdfs:domain|schema:domainIncludes)/(^rdfs:subClassOf)* "
                          "<" rdf-type "> ."))

                   :item
                   (when-let [rdf-type (:rdf-type opts)]
                     (str "?entity "
                          "a/(^rdfs:subClassOf)* "
                          "<" rdf-type "> ."))

                   nil)]
    (str
     "PREFIX text: <http://jena.apache.org/text#> "
     "SELECT DISTINCT ?entity ?label ?description ?match "
     "FROM <urn:x-arq:DefaultGraph> "
     "FROM <urn:x-arq:UnionGraph> "
     "WHERE {"
     (when-let [line (text-query-line query)]
       (str "{"
            "  SELECT ?entity (SAMPLE(?match_) AS ?match) WHERE {"
            line
            "  }"
            "  GROUP BY ?entity"
            "}"))
     (case target
       :predicate "?entity a/rdfs:subClassOf* rdf:Property ."
       :type      "?entity a/rdfs:subClassOf* rdfs:Class ."
       "FILTER NOT EXISTS { ?entity a/rdfs:subClassOf* rdf:Property . }")
     (when validity
       (str "OPTIONAL {"
            validity
            "  BIND(1 as ?valid)"
            "}"))
     "  OPTIONAL {"
     "    ?entity rdfs:label|schema:name ?label ."
     ;; TODO: Пересмотреть это ограничение -М 2024.11.23
     "    FILTER (LANG(?label) = \"ru\")"
     "  }"
     "  OPTIONAL {"
     "    ?entity rdfs:comment|schema:description ?description ."
     "    FILTER (LANG(?description) = \"ru\")"
     "  }"
     "}"
     (when validity "ORDER BY DESC(?valid) ")
     "LIMIT " limit)))

(defn suggest
  [{{:strs [target q initial-suggest] :as params} :query-params
    ::r/keys [router]
    :as req}
   res raise]
  (if (empty? target)
    (raise (ex-info "No target= is provided." {}))
    (if (and (empty? q) (not= "true" initial-suggest))
      (res {:status 200
            :body   ""})
      (-> (p/let [opts   (case target
                           "predicate" {:target   :predicate
                                        :query    q
                                        :rdf-type (get params "type")}
                           "item"      {:target   :item
                                        :query    q
                                        :rdf-type (get params "type")
                                        :suggest-new-item (get params "suggest-new-item")}
                           "type"      {:target   :type
                                        :query    q}
                           (throw (ex-info (str "Unknown target: " target) {})))
                  query  (make-query opts)
                  {{:keys [bindings]} :results} (ts/get-bindings query)
                  view (with-request-data req
                         (cond
                           (seq bindings)
                           (render-suggestions bindings)

                           (and (:suggest-new-item opts)
                                (not (re-find #"^https?://" q)))
                           (let [href (get-route-url router
                                                     ::new-item/new-item
                                                     :query-params {:label q})]
                             (render-create-new-item {:href  href
                                                      :query q}))

                           :else nil))]
            (-> (res/ok view)
                (res/content-type "text/html")
                res))
          (p/catch raise)))))

(def routes {:get        #'suggest
             :middleware [[wrap-params]]})
