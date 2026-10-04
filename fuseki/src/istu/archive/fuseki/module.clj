(ns istu.archive.fuseki.module
  (:require [istu.archive.fuseki.forest :as forest]
            [istu.archive.fuseki.functions.datetime :as dt])
  (:import [org.apache.jena.fuseki.main.sys FusekiAutoModule]
           [org.apache.jena.query ARQ]
           [org.apache.jena.sparql.pfunction PropertyFunctionRegistry]))

(deftype CustomModule []
  :load-ns true
  FusekiAutoModule
  (name [_] "ISTU Photo Archive")
  (prepare [_ builder _dataset-names _config-model]
    (.registerOperation builder forest/operation forest/handler)
    (let [ctx         (ARQ/getContext)
          pf-registry (PropertyFunctionRegistry/chooseRegistry ctx)]
      (dt/register pf-registry)
      (PropertyFunctionRegistry/set ctx pf-registry))))
