(ns istu.archive.routes.index
  (:require [clojure.core.match :refer [match]]
            [clojure.math :as math]
            [clojure.string :as str]
            [hiccups.runtime :refer [render-html]]
            [istu.archive.macros.request :refer [with-request-data]]
            [istu.archive.middleware :refer [wrap-query-entries]]
            [istu.archive.rdf.ns :as ns]
            [istu.archive.routes.core :refer [get-route-url]]
            [istu.archive.services.postgres :as pg]
            [istu.archive.services.triplestore :as ts]
            [istu.archive.views.index :as index]
            [macchiato.util.response :as res]
            [promesa.core :as p]
            [reitit.core :as r]))

(def ^:private vocabular-cached (atom nil))
(def ^:private vocabular-schema
  {:gender        (ns/expand :schema/GenderType)
   :academic-rank (ns/expand :ia/AcademicRank)})

(defn instances-query [type]
  (p/let [query    (str "SELECT ?item ?label "
                        "FROM <urn:x-arq:DefaultGraph>"
                        "FROM <urn:x-arq:UnionGraph>"
                        "WHERE {"
                        "  ?item a <" type "> ;"
                        "        (schema:name|rdfs:label) ?label ."
                        "}")
          bindings (ts/get-bindings query)]
    (reduce (fn [acc {:keys [item label]}]
              (let [iri    (:value item)
                    label' (:value label)
                    lang   (keyword (:xml:lang label))]
                (assoc-in acc [iri :label lang] label')))
            {} (-> bindings :results :bindings))))

(defn get-vocabular []
  (if @vocabular-cached
    @vocabular-cached
    (p/let [results   (->> vocabular-schema
                           (map (fn [[k v]]
                                  (p/then' (instances-query v) #(vector k %))))
                           p/all)
            vocabular (into {} results)]
      (reset! vocabular-cached vocabular)
      @vocabular-cached)))

(defn- get-field [m k]
  (let [v (get m k)]
    (if (empty? v) nil v)))

(defn- check-field [k expected]
  (when-not (= k expected)
    (throw (ex-info (str "Expected " expected " parameter, got: " k) {}))))

(defn- parse-persons [pairs]
  (loop [pairs  pairs
         result #{}]
    (if (empty? pairs)
      result
      (let [[[k kind] & pairs] pairs]
        (check-field k "person")
        (case kind
          "exact"
          (let [[[k iri] & pairs] pairs]
            (check-field k "item")
            (recur pairs (conj result iri)))

          "match"
          (let [params (into {} (take 10 pairs))
                pairs  (nthrest pairs 10)
                m      {:family-name     (get-field params "family-name")
                        :given-name      (get-field params "given-name")
                        :patronymic-name (get-field params "patronymic-name")
                        :gender          (get-field params "gender")
                        :workplace       (get-field params "workplace")
                        :position        (get-field params "position")
                        :academic-rank   (get-field params "academic-rank")
                        :academic-degree (get-field params "academic-degree")
                        :award           (get-field params "award")
                        :participated    (get-field params "participated")}]
            (recur pairs (conj result m))))))))

(defn- parse-common [pairs]
  (when (seq pairs)
    (let [common (into {} (take 6 pairs))
          pairs  (nthrest pairs 6)]
      {:since    (get-field common "start-date")
       :until    (get-field common "end-date")
       :event    (get-field common "event")
       :location (get-field common "location")
       :genre    (get-field common "genre")
       :sort     (case (get-field common "sort")
                   "recently-uploaded" '(:upload :desc)
                   "long-ago-uploaded" '(:upload :asc)
                   "newest"            '(:create :desc)
                   "oldest"            '(:create :asc))
       :person   (parse-persons pairs)})))

(defn- parse-query [pairs]
  (match pairs
    ([["page" page] & pairs'] :seq)
    (merge {:page (parse-double page)}
           (parse-common pairs'))

    :else
    (parse-common pairs)))

#_{:clj-kondo/ignore [:uninitialized-var]}
(def ^:dynamic ^:private var-counter)

(defn- alloc-variable []
  (let [c @var-counter]
    (swap! var-counter inc)
    (str "?v" c)))

(defn- sparql-escape-literal [s]
  (-> s (str/replace #"\"" "\\\"")))

(defn- sparql-escape-iri [s]
  (-> s
      (str/replace #"<" "%3C")
      (str/replace #">" "%3E")))

(defn- iri [s] (str "<" (sparql-escape-iri s) ">"))

(defn- sparql-check-name [subject prop value]
  (let [object (alloc-variable)
        value' (-> value sparql-escape-literal str/lower-case)]
    (str subject " " prop " " object " ."
         "FILTER (LCASE(" object ") = \"" value' "\")")))

(defn- generate-sparql-person [person]
  (if (map? person)
    (let [pv (alloc-variable)]
      (str "?photo ia:hasDepiction/ia:depictedObject " pv " ."
           (when-let [value (:family-name person)]
             (sparql-check-name pv "schema:familyName" value))
           (when-let [value (:given-name person)]
             (sparql-check-name pv "schema:givenName" value))
           (when-let [value (:patronymic-name person)]
             (sparql-check-name pv "ia:patronymicName" value))
           (when-let [value (:gender person)]
             (str pv " schema:gender " (iri value) " ."))
           (when-let [value (:workplace person)]
             (str pv " "
                  "org:hasMembership/"
                  "org:organization/"
                  "schema:parentOrganization* " (iri value) " ."))
           (when-let [value (:position person)]
             (str pv " org:hasMembership/org:role " (iri value) " ."))
           (when-let [value (:academic-rank person)]
             (str pv " ia:academicRank " (iri value) " ."))
           (when-let [value (:academic-degree person)]
             (str pv " ia:academicDegree/skos:broader* " (iri value) " ."))
           (when-let [value (:award person)]
             (str pv " ia:awardReceived " (iri value) " ."))
           (when-let [value (:participated person)]
             (str pv " ia:participant/ia:partOf* " (iri value) " ."))))
    (str "?photo ia:hasDepiction/ia:depictedObject " (iri person) " .")))

(def ^:private items-per-page 24)

(defn- generate-sparql [query & {:keys [estimate-count?]}]
  (binding [var-counter (atom 0)]
    (str (if estimate-count?
           "SELECT (COUNT(DISTINCT ?photo) AS ?count) "
           "SELECT DISTINCT ?photo ?date ?unit ")
         "WHERE {"
         "  ?photo a schema:Photograph ;"
         "         schema:uploadDate ?uploaded ."
         "  OPTIONAL {"
         "    ?photo ia:dateCreated ?dateCreated ."
         "    ?dateCreated ia:toISODateTime (?date ?unit) ."
         "  }"
         (when-let [since (:since query)]
           (str "FILTER(?date >= \"" since "T00:00:00Z\"^^xsd:dateTime)"))
         (when-let [until (:until query)]
           (str "FILTER(?date < \"" until "T24:00:00Z\"^^xsd:dateTime)"))
         (case (first (:sort query))
           :upload "BIND(?uploaded AS ?sorter)"
           :create (case (second (:sort query))
                     :asc  "BIND(COALESCE(?date, \"9999-12-31T24:00:00Z\"^^xsd:dateTime) AS ?sorter)"
                     :desc "BIND(?date AS ?sorter)"))
         ;; TODO: Could there be an injection? -M 2025.02.16
         (when-let [event (:event query)]
           (str "?photo schema:recordedAt <" event "> ."))
         (when-let [location (:location query)]
           (str "?photo schema:contentLocation <" location "> ."))
         (when-let [genre (:genre query)]
           (str "?photo schema:genre <" genre "> ."))

         (apply #'str (map generate-sparql-person (:person query)))

         "}"
         (case (second (:sort query))
           :asc  "ORDER BY ASC(?sorter)"
           :desc "ORDER BY DESC(?sorter)")
         (when-not estimate-count?
           (str "LIMIT " items-per-page " "
                "OFFSET " (* (or (:page query) 0) items-per-page))))))

(defn get-paginated-results [query]
  (p/let [page            (or (:page query) 0)
          sparql-estimate (generate-sparql query :estimate-count? true)
          response        (ts/get-bindings sparql-estimate)
          photo-count     (-> response
                              :results :bindings
                              first
                              :count :value parse-long)
          total-pages     (math/ceil (/ photo-count items-per-page))]
    (cond
      (zero? photo-count)
      {:bindings [], :page page, :total-pages total-pages}

      (or (neg? page) (>= page total-pages 1))
      (throw (ex-info (str "Page " page " out of bounds.") {}))

      :else
      (p/let [sparql-actual (generate-sparql query)
              response      (ts/get-bindings sparql-actual)
              bindings      (-> response :results :bindings)]
        {:bindings    bindings
         :page        page
         :total-pages total-pages}))))

(def ^:private default-query {:sort '(:upload :desc)})

(defn- url-for-page [router name page entries]
  (let [entries-without-page (match entries
                               ([["page" _] & entries'] :seq) entries'
                               :else                          entries)
        new-entries          (cons ["page" page] entries-without-page)]
    (get-route-url router name :query-params new-entries)))

(defn index
  [{:keys [query-entries], {:strs [hx-request]} :headers
    {{:keys [name]} :data} ::r/match,
    ::r/keys [router], :as req} res raise]
  (-> (p/let [vocabular (get-vocabular)
              query     (merge default-query (parse-query query-entries))
              results   (get-paginated-results query)
              page-href #(url-for-page router name % query-entries)
              motd      (pg/get-ui-message "motd")
              opts      (assoc results
                               :motd      motd
                               :vocabular vocabular
                               :page-href page-href)]
        (-> (with-request-data req
              (if hx-request
                (render-html (index/render-results opts))
                (index/render-index opts)))
            (res/ok)
            (res/content-type "text/html")
            res))
      (p/catch (fn [ex]
                 (let [status (or (-> ex ex-data :http-status) 500)
                       msg    (ex-message ex)]
                   (res
                    (with-request-data req
                      (if hx-request
                        {:status  status
                         :headers {"Content-Type" "text/plain"}
                         :body    msg}
                        {:status  status
                         :headers {"Content-Type" "text/html"}
                         :body    (index/render-index {:error msg})}))))))))

(def routes {:name       ::index
             :get        #'index
             :middleware [[wrap-query-entries]]})
