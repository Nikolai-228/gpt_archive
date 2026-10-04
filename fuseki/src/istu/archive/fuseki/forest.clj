(ns istu.archive.fuseki.forest
  "Операция Fuseki, предоставляющая описание сущности в виде «леса», который затем
  можно использовать с помощью функций в `istu.archive.rdf.forest`. При запросе
  вычисляется замыкание по заданному именованному узлу, подробнее
  см. `istu.archive.fuseki.closure/closure`."
  (:require [cognitect.transit :as transit]
            [istu.archive.fuseki.base32 :as b32]
            [istu.archive.fuseki.closure :refer [closure]]
            [istu.archive.fuseki.http :refer [http-action]]
            [istu.archive.fuseki.transactions :refer [with-transaction]]
            [istu.archive.rdf.forest :as frs]
            [istu.archive.rdf.impl.jena :as rdf]
            [istu.archive.rdf.model :refer [clj->term term->clj]]
            [istu.archive.rdf.ns :as ns]
            [nano-id.core :as nano-id]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.request :as r])
  (:import [java.io ByteArrayOutputStream]
           [org.apache.jena.fuseki.server Operation]
           [org.apache.jena.fuseki.servlets ServletOps]
           [org.apache.jena.graph Node]
           [org.apache.jena.sparql.core DatasetGraph Quad]))

(def operation
  (Operation/alloc (ns/expand :ia/forest)
                   "forest" "Forest representation"))

(def ^:private rdfs-label (rdf/named-node (ns/expand :rdfs/label)))
(def ^:private skos-pref-label (rdf/named-node (ns/expand :skos/prefLabel)))
(def ^:private schema-name (rdf/named-node (ns/expand :schema/name)))
(def ^:private label-properties (list rdfs-label skos-pref-label schema-name))
(def ^:private schema-description (rdf/named-node (ns/expand :schema/description)))
(def ^:private skos-alt-label (rdf/named-node (ns/expand :skos/altLabel)))

(def ^:private uid32 (nano-id/custom b32/alphabet 16))

(defn- find-label-quads [^DatasetGraph dataset ^Node node]
  (mapcat #(rdf/match dataset node % nil)
          label-properties))

(defn- quad-and-labels [^DatasetGraph dataset ^Quad quad]
  (let [p (rdf/predicate quad)
        o (rdf/object    quad)]
    (concat (list quad)
            (find-label-quads dataset p)
            (find-label-quads dataset o))))

(defn- should-recurse? [term]
  (and (qualified-keyword? term)
       (= "iav" (namespace term))))

(defn- make-quad [s p o]
  (Quad/create rdf/default-graph
               (clj->term s)
               (clj->term p)
               (clj->term o)))

(defn- return-quad [^Quad quad] (pop (term->clj quad)))

(defn- tree? [{:keys [type]}] (= :tree type))

(declare flatten-spo)
(defn- tree->quads [tree s & {:keys [generate-iri]}]
  (mapcat (fn [[p os]]
            (if (set? os)
              (mapcat #(flatten-spo s p % :generate-iri generate-iri) os)
              (flatten-spo s p os :generate-iri generate-iri)))
          (dissoc tree :type)))

(defn- flatten-spo [s p o & {:keys [generate-iri]}]
  (cond
    (nil? o) nil

    (tree? o)
    (let [node (if generate-iri
                 (generate-iri s p o)
                 (rdf/blank-node))]
      (concat (list (make-quad s p node))
              (tree->quads o node)))

    :else (list (make-quad s p o))))

(defn make-value-iri [s p o]
  (let [p (if (keyword? p)
            p
            (ns/iri->kwi p))
        h (->> (hash o)
               (b32/encode-number)
               (take 8)
               (apply #'str))]
    (keyword "iav"
             (str (name s) "_" (name p) "_" h))))

(defn- set-label-property [^DatasetGraph dataset prop subject language value]
  (let [new (when value
              (make-quad subject prop (rdf/literal value :language language)))]
    (when (and new (.contains dataset new))
      (ServletOps/errorOccurred "Forest: label already set."))
    (let [old (filter #(-> % rdf/object rdf/language (= (name language)))
                      (rdf/match dataset (clj->term subject) prop nil))]
      {:deleted (doall
                 (for [quad old]
                   (do (.delete dataset quad)
                       (return-quad quad))))
       :added (when new
                (.add dataset new)
                (list (return-quad new)))})))

(defn- gen-top-level-iri [^DatasetGraph dataset]
  ;; На практике коллизия скорее всего никогда не произойдёт, но теоретически
  ;; может случиться, поэтому пробуем несколько раз
  (loop [trial 3]
    (when (zero? trial)
      (throw (ex-info "Could not generate top-level IRI." {})))
    (let [iri  (keyword "iao" (uid32))
          node (clj->term iri)]
      (if (.contains dataset rdf/default-graph node nil nil)
        (recur (dec trial))
        iri))))

(defmulti process-action (fn [_dataset action] (:action action)))

(defmethod process-action :create-entity
  [^DatasetGraph dataset {:keys [tree]}]
  (let [iri      (gen-top-level-iri dataset)
        mk-label (fn [prop [lang value]]
                   (make-quad iri prop
                              (rdf/literal value :language lang)))
        labels       (map #(mk-label schema-name        %) (:label tree))
        descriptions (map #(mk-label schema-description %) (:description tree))
        aliases      (mapcat (fn [[lang strings]]
                               (map #(mk-label skos-alt-label [lang %]) strings))
                             (:alias tree))
        tree' (dissoc tree :label :description :alias)
        quads (concat (tree->quads tree' iri :generate-iri make-value-iri)
                      labels descriptions aliases)]
    {:iri   iri
     :added (doall
             (for [quad quads]
               (do (.add dataset quad)
                   (return-quad quad))))}))

(defmethod process-action :delete-entity
  [^DatasetGraph dataset {:keys [iri]}]
  (when (nil? iri)
    (ServletOps/errorBadRequest "Forest: no IRI specified for the :delete-entity action."))
  (let [quads (closure dataset (clj->term iri))]
    (when (empty? quads)
      (ServletOps/errorNotFound "Forest: entity not found."))
    {:deleted
     (doall
      (for [quad quads]
        (do (.delete dataset quad)
            (return-quad quad))))}))

(defmethod process-action :set-label
  [^DatasetGraph dataset {:keys [subject language value]}]
  (set-label-property dataset schema-name subject language value))

(defmethod process-action :set-description
  [^DatasetGraph dataset {:keys [subject language value]}]
  (set-label-property dataset schema-description subject language value))

(defmethod process-action :set-alias
  [^DatasetGraph dataset {:keys [subject language old new]}]
  (let [old-quad (when old
                   (make-quad subject skos-alt-label
                              (rdf/literal old :language language)))]
    (when (and old (not (.contains dataset old-quad)))
      (ServletOps/errorNotFound "Forest: no such old alias."))
    (let [new-quad (when new
                     (make-quad subject skos-alt-label
                                (rdf/literal new :language language)))]
      (when (and new (.contains dataset new-quad))
        (ServletOps/errorOccurred "Forest: new alias exists."))
      {:deleted (when old
                  (.delete dataset old-quad)
                  (list (return-quad old-quad)))
       :added (when new
                (.add dataset new-quad)
                (list (return-quad new-quad)))})))

(defmethod process-action :add-stmt
  [^DatasetGraph dataset {:keys [subject predicate object]}]
  (let [to-add (flatten-spo subject predicate object
                            :generate-iri make-value-iri)]
    (when (.contains dataset (first to-add))
      (ServletOps/errorOccurred "Forest: statement exists."))
    {:added
     (doall
      (for [quad to-add]
        (do (.add dataset quad)
            (return-quad quad))))}))

(defmethod process-action :delete-stmt
  [^DatasetGraph dataset {:keys [subject predicate object]}]
  (let [main-quad (make-quad subject predicate object)]
    (when-not (.contains dataset main-quad)
      (ServletOps/errorNotFound "Forest: no such statement."))
    (let [to-delete (if (should-recurse? object)
                      (concat (list main-quad)
                              (closure dataset (rdf/object main-quad)))
                      (list main-quad))]
      {:deleted
       (doall
        (for [quad to-delete]
          (do (.delete dataset quad)
              (return-quad quad))))})))

(def handler
  (http-action
   {:get (fn [{:keys [jena/dataset],
               {:strs [id]} :query-params}]
           (when-not id
             (ServletOps/errorBadRequest "Query parameter ?id= not specified."))
           (let [node   (rdf/named-node (str (ns/expand :iao) id))
                 forest (with-transaction dataset :read
                          (transduce (comp
                                      (mapcat #(quad-and-labels dataset %))
                                      (map term->clj))
                                     (completing frs/add-triple)
                                     {}
                                     (closure dataset node)))
                 out    (ByteArrayOutputStream. 4096)
                 writer (transit/writer out :json)]
             (transit/write writer forest)
             {:status  200
              :headers {"Content-Type" "application/transit+json"}
              :body    (.toByteArray out)}))

    :post (fn [{:keys [jena/dataset body] :as req}]
            (when-not (= "application/transit+json" (r/content-type req))
              (ServletOps/errorBadRequest "Forest: content type must be application/transit+json"))
            (let [reader (transit/reader body :json)
                  action (transit/read reader)
                  result (with-transaction dataset :write ; TODO: В TDB2 лучше
                                                          ; использовать :read-promote
                           (process-action dataset action))
                  out    (ByteArrayOutputStream. 4096)
                  writer (transit/writer out :json)]
              (transit/write writer result)
              {:status  200
               :headers {"Content-Type" "application/transit+json"}
               :body    (.toByteArray out)}))}
   [[wrap-params]]))
