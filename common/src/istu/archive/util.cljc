(ns istu.archive.util
  (:require [clojure.string :as str]))

(defn fix-link [iri]
  (str/replace-first iri "http://archive.istu.ru/" "/"))
