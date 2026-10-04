(ns istu.archive.rdf.impl.jena
  "Обёртка для работы с наборами данных RDF в Apache Jena."
  (:require [clojure.string :as str]
            [istu.archive.rdf.model :as rdfm :refer [TermToClojure term->clj clj->term]])
  (:import [org.apache.jena.graph
            Node
            Node_Blank
            Node_Literal
            Node_Triple
            Node_URI
            NodeFactory]
           [org.apache.jena.datatypes TypeMapper RDFDatatype]
           [org.apache.jena.sparql.core Quad DatasetGraph]))

(def default-graph Quad/defaultGraphIRI)

;;;; Конструкторы и предикаты
;;;
;;; См. <https://rdf.js.org/data-model-spec/#datafactory-interface>.

(defn named-node  [value]   (NodeFactory/createURI value))
(defn named-node? [^Node o] (.isURI o))
(defn make-datatype [^String iri]
  (.getSafeTypeByName (TypeMapper/getInstance) iri))

(defn blank-node
  ([]      (NodeFactory/createBlankNode))
  ([value] (NodeFactory/createBlankNode value)))
(defn blank-node? [^Node o] (.isBlank o))

(defn literal
  [^String value & {:keys [language datatype direction]}]
  (cond
    direction (NodeFactory/createLiteralDirLang value
                                                (name language)
                                                (name direction))
    language  (NodeFactory/createLiteralLang value (name language))
    datatype  (NodeFactory/createLiteralDT value datatype)
    :else     (NodeFactory/createLiteralString value)))
(defn literal? [^Node o] (.isLiteral o))

(defn triple  [s p o]   (NodeFactory/createTripleNode s p o))
(defn triple? [^Node o] (.isNodeTriple o))

;;;; Геттеры

(defprotocol Valued (value [x]))
(extend-protocol Valued
  nil          (value [_] nil)
  Node_URI     (value [x] (.getURI x))
  Node_Blank   (value [x] (.getBlankNodeLabel x))
  Node_Literal (value [x] (.getLiteralLexicalForm x))
  RDFDatatype  (value [x] (.getURI x)))

(defprotocol ITriple
  (subject   [t])
  (predicate [t])
  (object    [t]))

(extend-protocol ITriple
  Node_Triple
  (subject   [t] (-> t .getTriple .getSubject))
  (predicate [t] (-> t .getTriple .getPredicate))
  (object    [t] (-> t .getTriple .getObject))

  Quad
  (subject   [t] (.getSubject   t))
  (predicate [t] (.getPredicate t))
  (object    [t] (.getObject    t)))

(defn graph [^Quad q] (.getGraph q))

(defn language [^Node_Literal t]
  (-> t .getLiteralLanguage str/lower-case))
(defn datatype [^Node_Literal t]
  (.getLiteralDatatype t))
(defn direction [^Node_Literal t]
  (some-> t .getLiteralTextDirection .toString))

;;;; Наборы данных

(defn match
  [^DatasetGraph ds s p o & [g]]
  (iterator-seq (.find ds g s p o)))

(extend-protocol TermToClojure
  nil
  (term->clj [x] x)

  Node_URI
  (term->clj [x] (rdfm/named-node-canonical (value x)))

  Node_Blank
  (term->clj [x] (rdfm/blank-node (value x)))

  Node_Literal
  (term->clj [x]
    (let [val    (value x)
          dt-iri (-> x datatype value)
          o      {:type     :literal
                  :value    val
                  :datatype (rdfm/named-node-canonical dt-iri)}
          lang   (language x)
          dir    (direction x)]
      (cond-> o
        (seq lang) (assoc :language  (keyword lang))
        (seq dir)  (assoc :direction (keyword dir)))))

  Node_Triple
  (term->clj [x]
    (mapv term->clj
          ((juxt subject predicate object) x)))

  Quad
  (term->clj [x]
    (mapv term->clj
          ((juxt subject predicate object graph) x))))

(defmethod clj->term nil [x] x)

(defmethod clj->term :named-node [kwi-or-iri]
  (named-node (rdfm/full-iri kwi-or-iri)))

(defmethod clj->term :blank-node [kwi]
  (blank-node (name kwi)))

(defmethod clj->term :literal [{:keys [value datatype language direction]}]
  (literal value
           :datatype (make-datatype (rdfm/full-iri datatype))
           :language language
           :direction direction))
