(ns istu.archive.rdf.impl.n3
  "Обёртка для взаимодействия с библиотекой N3.js."
  (:require [istu.archive.rdf.model :as rdfm :refer [TermToClojure term->clj]]
            ["n3" :as n3]))

(def default-graph (n3/DataFactory.defaultGraph))

;;;; Конструкторы и предикаты
;;;
;;; См. <https://rdf.js.org/data-model-spec/#datafactory-interface>.

(defn named-node  [value] (n3/DataFactory.namedNode value))
(defn named-node? [o] (n3/Util.isNamedNode o))
(def  make-datatype named-node)

(defn blank-node
  ([]      (n3/DataFactory.blankNode))
  ([value] (n3/DataFactory.blankNode value)))
(defn blank-node? [o] (n3/Util.isBlankNode o))

(defn literal
  [value & {:keys [language datatype]}]
  {:pre [(not (and language datatype))
         (or (nil? datatype) (named-node? datatype))]}
  (n3/DataFactory.literal value
                          (cond
                            datatype datatype
                            language (name language)
                            :else    nil)))
(defn literal? [o] (n3/Util.isLiteral o))

(defn variable  [value] (n3/DataFactory.variable value))
(defn variable? [o]     (n3/Util.isVariable o))

(defn quad [s p o g]
  (n3/DataFactory.quad s p o (or g default-graph)))
(defn quad?   [o]     (instance? n3/Quad o))
(defn triple  [s p o] (quad s p o nil))
(def  triple? quad?)

;;;; Геттеры

(defn subject   [^n3/Quad t] (.-subject   t))
(defn predicate [^n3/Quad t] (.-predicate t))
(defn object    [^n3/Quad t] (.-object    t))
(defn graph     [^n3/Quad t] (.-graph     t))
(defn value     [^n3/Term t] (.-value     t))
(defn language  [^n3/Literal t] (.-language t))
(defn datatype  [^n3/Literal t] (.-datatype t))
(defn term-type [^n3/Term t]
  (case (.-termType t)
    "NamedNode"    :named-node
    "BlankNode"    :blank-node
    "Literal"      :literal
    "Variable"     :variable
    "Quad"         :quad
    "DefaultGraph" :default-graph
    nil))

;; Даёт возможность осуществлять деструктуризацию
(extend-type n3/Quad
  IIndexed
  (-nth
    ([t n]
     (condp == n
       0 (subject   t)
       1 (predicate t)
       2 (object    t)
       3 (graph     t)
       (throw (js/Error. "Index out of bounds."))))
    ([t n not-found]
     (condp == n
       0 (subject   t)
       1 (predicate t)
       2 (object    t)
       3 (graph     t)
       not-found))))

;;;; Наборы данных

(extend-type n3/Store
  ICollection
  (-conj [^n3/Store coll quad]
    (if (instance? n3/Quad quad)
      (.add coll quad)
      (throw (js/Error. (str quad " is not a quad"))))))

(defn match [ds s p o & [g]] (.match ds s p o g))

(defn parse
  "Разбирает данные RDF в набор троек.

  Если задан параметр `:callback`, анализ осуществляется
  асинхронно (см. https://github.com/rdfjs/N3.js?tab=readme-ov-file#from-an-rdf-document-to-quads).
  Иначе возвращается массив троек."
  [data & {:keys [format callback]}]
  (let [parser (n3/Parser. #js {:format format})]
    (.parse parser data callback)))

(extend-protocol TermToClojure
  nil
  (term->clj [x] x)

  n3/NamedNode
  (term->clj [x] (rdfm/named-node-canonical (value x)))

  n3/DefaultGraph
  (term->clj [x] (value x))

  n3/BlankNode
  (term->clj [x] (rdfm/blank-node (value x)))

  n3/Literal
  (term->clj [x]
    (let [val    (value x)
          dt-iri (some-> x datatype value)
          o      {:type     :literal
                  :value    val
                  :datatype (rdfm/named-node-canonical dt-iri)}
          lang   (language x)]
      (if (seq lang)
        (assoc o :language (keyword lang))
        o)))

  n3/Quad
  (term->clj [x]
    (mapv term->clj
          ((juxt subject predicate object graph) x))))
