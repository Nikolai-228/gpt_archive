(ns istu.archive.config
  (:require [macchiato.env]
            [mount.core :refer [defstate]]))

(defstate env :start (macchiato.env/env))
