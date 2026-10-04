(ns istu.archive.macros.log
  (:require [cognitect.transit :as transit])
  #?(:cljs (:require-macros [istu.archive.macros.log])))

(defmacro log-action! [user result]
  `(let [writer# (transit/writer :json)
         {username# :username} ~user]
     (when-let [iri# (get ~result :iri)]
       (println "CREATE" username# (transit/write writer# iri#)))
     (doseq [triple# (get ~result :added)]
       (println "UPDATE" username# "+" (transit/write writer# triple#)))
     (doseq [triple# (get ~result :deleted)]
       (println "UPDATE" username# "-" (transit/write writer# triple#)))))
