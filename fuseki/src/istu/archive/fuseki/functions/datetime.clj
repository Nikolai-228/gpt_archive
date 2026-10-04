(ns istu.archive.fuseki.functions.datetime
  (:require [istu.archive.fuseki.closure :refer [closure]]
            [istu.archive.rdf.forest :as frs]
            [istu.archive.rdf.impl.jena :as rdf]
            [istu.archive.rdf.model :refer [term->clj clj->term]]
            [istu.archive.rdf.repr.time :as repr-time]
            [istu.archive.rdf.ns :as ns])
  (:import [org.apache.jena.graph Node]
           [org.apache.jena.sparql.core Var]
           [org.apache.jena.sparql.engine ExecutionContext]
           [org.apache.jena.sparql.engine.binding Binding]
           [org.apache.jena.sparql.engine.iterator QueryIterSingleton]
           [org.apache.jena.sparql.expr ExprEvalTypeException]
           [org.apache.jena.sparql.pfunction
            PFuncSimpleAndList
            PropertyFunction
            PropertyFunctionFactory
            PropertyFunctionRegistry
            PropFuncArg]
           [org.apache.jena.query
            QueryBuildException
            QueryExecException]))

(def xsd-date-time (rdf/make-datatype (ns/expand :xsd/dateTime)))

(defn- make-variable [^Node node ^String name]
  (when-not (.isVariable node)
    (throw (QueryExecException. (str name " is not a variable: " node))))
  (Var/alloc node))

(defn- ensure-concrete [^PropFuncArg arg msg]
  (when-not (.isNode arg)
    (throw (QueryBuildException. msg)))
  (let [node (.getArg arg)]
    (if (.isConcrete node)
      node
      (throw (QueryExecException. msg)))))

(defn iso-datetime-function []
  (proxy [PFuncSimpleAndList] []
    (execEvaluated [^Binding          parent
                    ^PropFuncArg      subject
                    ^Node             _predicate
                    ^PropFuncArg      object
                    ^ExecutionContext ctx]
      (when-not (= 2 (.getArgListSize object))
        (throw (QueryExecException. "ia:toISODateTime expects a list of size 2 as the object.")))
      (let [subject  (ensure-concrete subject "ia:toISODateTime expects a bound variable as the subject.")
            date-var (make-variable (.getArg object 0) "Date")
            unit-var (make-variable (.getArg object 1) "Unit type")
            dataset  (.getDataset ctx)
            forest   (transduce (map term->clj)
                                (completing frs/add-triple)
                                {}
                                (closure dataset subject))
            sq       (frs/squirrel forest (term->clj subject))]
        (when-not (->> @sq :rdf/type (some #{:time/Instant}))
          (throw (ExprEvalTypeException. "ia:toISODateTime expects a time:Instant.")))
        (let [date-value (-> sq
                             repr-time/instant->properties
                             repr-time/properties->iso-string
                             (rdf/literal :datatype xsd-date-time))
              unit-value (-> sq :time/inDateTime :time/unitType frs/node clj->term)
              binding    (-> (Binding/builder parent)
                             (.add date-var date-value)
                             (.add unit-var unit-value)
                             (.build))]
          (QueryIterSingleton/create binding ctx))))))

(defn register [^PropertyFunctionRegistry pf-registry]
  (.put pf-registry
        (ns/expand :ia/toISODateTime)
        (reify PropertyFunctionFactory
          (^PropertyFunction create [_this ^String _uri]
           (iso-datetime-function)))))
