(ns istu.archive.routes.logout
  (:require [macchiato.util.response :as res]))

(defn logout [req res raise]
  (-> (res/see-other "/")
      (assoc :user-data nil)
      res))

(def routes {:get #'logout})
