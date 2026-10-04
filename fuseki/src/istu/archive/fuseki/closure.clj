(ns istu.archive.fuseki.closure
  (:require [clojure.string :as str]
            [istu.archive.rdf.impl.jena :as rdf]
            [istu.archive.rdf.ns :as ns])
  (:import [org.apache.jena.graph Node]
           [org.apache.jena.sparql.core DatasetGraph Quad]))

(defn- should-traverse? [^Quad n]
  ;; Проходить по пустым узлам или именованным узлам из пространства iav
  (or (rdf/blank-node? n)
      (and (rdf/named-node? n)
           (str/starts-with? (rdf/value n) (ns/expand :iav)))))

(defn closure
  "Вычисляет замыкание по заданному узлу графа. Замыкание рекурсивно включает в
  себя все тройки (четвёртки), субъектом которых выступает данный узел, а если
  объектом какой-либо из них является пустой узел либо именованный узел с
  пространством имён `iav:`, то также и их тройки."
  ([^DatasetGraph dataset ^Node node]
   (closure dataset node (transient #{})))
  ([^DatasetGraph dataset ^Node node visited?]
   (when (nil? node)
     (throw (ex-info "istu.archive.fuseki.closure/closure: Node is nil" {})))
   (when-not (visited? node)
     #_:clj-kondo/ignore (conj! visited? node)
     (->> (rdf/match dataset node nil nil rdf/default-graph)
          (mapcat (fn [q]
                    (let [o (rdf/object q)]
                      (concat (list q)
                              (when (should-traverse? o)
                                (closure dataset o visited?))))))))))
