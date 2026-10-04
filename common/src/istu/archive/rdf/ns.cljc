(ns istu.archive.rdf.ns
  "Модуль для определения пространств имён (номенклатур) RDF."
  #?(:cljs (:require-macros [istu.archive.rdf.ns]))
  (:require [clojure.string :as str]))

(def ^:private prefix->namespace (atom {}))
(def ^:private namespace->prefix (atom {}))
(def ^:private ns-re-pattern (atom nil))

;; Регулярные выражения быстрее линейного поиска и наивной реализации
;; префиксного дерева
(defn- build-re-pattern! []
  (reset! ns-re-pattern
          (re-pattern (str "^("
                           (str/join "|" (keys @namespace->prefix))
                           ")(.*)"))))

(defn register-ns!
  "Регистрирует пространства имён (номенклатуры) RDF. Принимает парные аргументы
  вида :префикс \"IRI пространства имён\" в произвольном количестве."
  {:style/indent 0}
  [& kvs]
  (let [entries (->> kvs
                     (partition 2)
                     (map (fn [[k v]] [(name k) v])))]
    (swap! prefix->namespace into entries)
    (swap! namespace->prefix into (map (fn [[k v]] [v k]) entries)))
  (build-re-pattern!))

(register-ns!
  :xsd    "http://www.w3.org/2001/XMLSchema#"
  :rdf    "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  :rdfs   "http://www.w3.org/2000/01/rdf-schema#"
  :owl    "http://www.w3.org/2002/07/owl#"
  :skos   "http://www.w3.org/2004/02/skos/core#"
  :foaf   "http://xmlns.com/foaf/0.1/"
  :org    "http://www.w3.org/ns/org#"
  :time   "http://www.w3.org/2006/time#"
  :schema "http://schema.org/"
  :iao    "http://archive.istu.ru/object/"
  :ia     "http://archive.istu.ru/ns#"
  :iav    "http://archive.istu.ru/value/"
  :hint   "http://archive.istu.ru/ns/hint#"
  :geom   "http://archive.istu.ru/ns/geom#"
  :uncert "http://archive.istu.ru/ns/uncertainty#")

(defn prefix-registered? [prefix]
  (boolean (get @prefix->namespace prefix)))

(defn iri->kwi
  "Преобразует IRI в идентификатор-ключевое слово, выделяя префикс пространства имён.

  Пространства имён должны быть зарегистрированы с помощью `register-ns!`. Если
  соответствующий префикс пространства имени не был найден, возвращается nil.

  # Пример

  ```clojure
  (iri->kwi \"http://schema.org/Person\")
  ;;=> :schema/Person
  ```"
  [^String iri]
  (when-let [[_ namespace local] (re-matches @ns-re-pattern iri)]
    (keyword (@namespace->prefix namespace)
             local)))

(defn kwi->iri
  "Преобразует идентификатор-ключевое слово в полный IRI. Обратная операция по
  отношению к `iri->kwi`.

  Пространства имён должны быть зарегистрированы с помощью `register-ns!`. Если
  соответствующий префикс пространства имени не был найден, возвращается nil."
  ^String
  [kw]
  {:pre [(qualified-keyword? kw)]}
  (when-let [iri (@prefix->namespace (namespace kw))]
    (str iri (name kw))))

(defn kwi->qname
  "Преобразует идентификатор-ключевое слово в qname (строка вида
  «префикс:локальная-часть»)."
  ^String
  [kw]
  {:pre [(qualified-keyword? kw)]}
  (str (namespace kw) ":" (name kw)))

(defn sparql-prefixes
  "Возвращает набор префиксов для запроса SPARQL."
  ^String
  []
  (->> @prefix->namespace
       (map (fn [[k v]] (str "PREFIX " k ": <" v ">")))
       (str/join "\n")))

#?(:clj
   (defmacro expand
     "Раскрывает идентификатор—ключевое слово в полный IRI во время чтения формы. Для
     раскрытия идентификаторов во время исполнения см. `kwi->iri`."
     [kw]
     {:pre [(keyword? kw)]}
     (if (qualified-keyword? kw)
       (kwi->iri kw)
       (@prefix->namespace (name kw)))))
