(ns istu.archive.macros.resource
  (:require [cljs.analyzer :as ana]
            [clojure.edn :as edn]
            [shadow.resource :as sr]))

(defmacro load-edn [path]
  (when-not (string? path)
    (throw (ana/error &env (str "istu.archive.resource/load-edn must be called with a string literal as an argument."))))
  (-> (sr/slurp-resource &env path)
      edn/read-string))
