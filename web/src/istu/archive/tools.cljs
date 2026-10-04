(ns istu.archive.tools
  "Всопомогательные утилиты."
  (:require [clojure.edn :as edn]
            [istu.archive.services.postgres :as pg]
            [mount.core :as mount]
            [promesa.core :as p]
            ["node:fs" :as fs]))

(defn stderr [& args]
  (binding [*print-fn* *print-err-fn*]
    (apply #'println args)))

(defn main [& _args]
  (mount/start)
  (let [text (fs/readFileSync 0 "utf-8")
        data (edn/read-string text)]
    (-> (p/loop [users data
                 count 0]
          (if (empty? users)
            (println "Registered" count "users.")
            (p/do
              (pg/register-user (first users))
              (p/recur (rest users) (inc count)))))
        (p/catch (fn [ex]
                   (stderr "ERROR:" (ex-message ex)))))))
