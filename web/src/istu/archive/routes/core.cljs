(ns istu.archive.routes.core
  (:require [reitit.core :as r]))

(defn get-route-url
  [router name & {:keys [path-params query-params]}]
  (-> router
      (r/match-by-name name path-params)
      (r/match->path query-params)))
