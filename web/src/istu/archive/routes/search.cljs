(ns istu.archive.routes.search
  (:require [clojure.string :as str]
            [istu.archive.i18n :refer [*lang*]]
            [istu.archive.macros.request :refer [with-request-data]]
            [istu.archive.services.triplestore :as ts]
            [istu.archive.views.layout :as layout]
            [istu.archive.views.search :refer [render-search]]
            [macchiato.middleware.params :refer [wrap-params]]
            [macchiato.util.response :as res]
            [promesa.core :as p]))

(defn escape-word [word]
  (-> word
      (str/replace #"\"" "\\\\\\\"")
      (str/replace #":" "\\\\:")
      (str/replace #"\(" "\\\\(")
      (str/replace #"\)" "\\\\)")
      (str/replace #"/" "\\\\/")))

;; TODO: consider expressing queries in SPARQL algebra-esque clojure -M 2024.10.07
(defn search-sparql [query]
  (let [words  (str/split query #"\s+")
        query' (->> words (map #'escape-word) (str/join " AND "))]
    (str "PREFIX text: <http://jena.apache.org/text#>"
         "SELECT ?entity ?name ?desc ?match "
         "FROM <urn:x-arq:DefaultGraph> "
         "WHERE {"
         "  {"
         "    SELECT ?entity (SAMPLE(?match_) AS ?match) WHERE {"
         "      (?entity ?score ?match_ ?g) text:query \"(" query' ")\" ."
         ;;     Брать только из графа по умолчанию (у них почему-то два идентификатора)
         "      FILTER (?g = <urn:x-arq:DefaultGraph> || ?g = <urn:x-arq:DefaultGraphNode>)"
         "    }"
         "    GROUP BY ?entity"
         "  }"
         "  OPTIONAL {"
         "    ?entity schema:name ?name ."
         "    FILTER (langMatches(lang(?name), \"" (name *lang*) "\"))"
         "  }"
         "  OPTIONAL {"
         "    ?entity schema:description ?desc ."
         "    FILTER langMatches(lang(?desc), \"" (name *lang*) "\")"
         "  }"
         "}")))

(defn search [{{:strs [q]} :query-params, :as req} res raise]
  (-> (p/let [start  (system-time)
              result (ts/get-bindings (search-sparql q))
              finish (system-time)
              opts   {:search   q
                      :elapsed  (- finish start)
                      :bindings (-> result :results :bindings)}]
        (binding [layout/*search* q]
          (-> (with-request-data req (render-search opts))
              (res/ok)
              (res/content-type "text/html")
              res)))
      (p/catch raise)))

(def routes {:get search :middleware [[wrap-params]]})
