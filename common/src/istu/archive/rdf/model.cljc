(ns istu.archive.rdf.model
  (:require [istu.archive.rdf.ns :as ns]))

(defn named-node-canonical
  "Преобразует IRI к «канонической» форме. Каноническая форма представляет собой
  квалифицированное ключевое слово, если данный IRI принадлежит какой-либо
  RDF-номенклатуре."
  [^String iri]
  (or (ns/iri->kwi iri) iri))

(defn full-iri [kwi-or-iri]
  (if (qualified-keyword? kwi-or-iri)
    (ns/kwi->iri kwi-or-iri)
    kwi-or-iri))

(def ^:private blank-ns ":blank")
(defn blank-node [id] (keyword blank-ns id))
(defn blank-node? [x]
  (and (qualified-keyword? x)
       (= blank-ns (namespace x))))

(defn triple?
  "Возвращает true, если данный объект является тройкой."
  [x]
  (and (vector? x) (= 3 (count x))))

(def string-type? #{:xsd/string :rdf/langString :rdf/dirLangString})

(defn resource?
  "Возвращает true, если данный объект указывает на ресурс и может выступать
  субъектом тройки."
  [x]
  (or (string? x) (qualified-keyword? x)  ; именованный или пустой узел
      (triple? x))) ; тройка (RDF-star)

(defn literal?
  "Возвращает true, если данный объект представляет собой литерал."
  [x]
  (= :literal (:type x)))

(defmulti
  ^{:doc "Конвертирует RDF-литерал в типизированное значение."}
  parse-literal :datatype)

(defmethod parse-literal :xsd/string [{:keys [value]}] value)
(defmethod parse-literal :xsd/decimal [{:keys [value]}]
  (parse-double value))
(defmethod parse-literal :xsd/nonNegativeInteger [{:keys [value]}]
  (parse-long value))

;;;; Time
(defmethod parse-literal :xsd/gYear [{:keys [value]}]
  (parse-long value))
(defmethod parse-literal :xsd/gMonth [{:keys [value]}]
  (parse-long (apply #'str (drop 2 value))))
(defmethod parse-literal :xsd/gDay [{:keys [value]}]
  (parse-long (apply #'str (drop 3 value))))

(defn term-type [term]
  (cond
    (blank-node? term)
    :blank-node

    (or (qualified-keyword? term) (string? term))
    :named-node

    (triple? term)
    :triple

    (literal? term)
    :literal

    :else nil))

(defprotocol TermToClojure
  "Протокол преобразования RDF-терминов в структуры данных Clojure."
  (term->clj [x]))

(defmulti ^{:doc "Преобразует структуры данных Clojure в соответствующие
  RDF-термин, который они представляют. Использовать только с объектами,
  созданными ранее через `term->clj`."}
  clj->term term-type)
