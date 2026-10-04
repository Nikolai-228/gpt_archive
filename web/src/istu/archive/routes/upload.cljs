(ns istu.archive.routes.upload
  (:require [istu.archive.api.form-data :refer [parse-object]]
            [istu.archive.macros.log :refer [log-action!]]
            [istu.archive.macros.request :refer [with-request-data]]
            [istu.archive.middleware :refer [wrap-multipart-entries]]
            [istu.archive.services.triplestore :as ts]
            [istu.archive.views.upload :refer [render-upload-form]]
            [macchiato.util.response :as res]
            [promesa.core :as p]
            ["node:fs/promises" :as fs]))

(defn ^:private now []
  (let [date (js/Date.)]
    {:type     :literal
     :datatype :xsd/dateTime
     :value    (.toISOString date)}))

(defn do-upload-file [path tree user]
  (p/let [action {:action :create-entity
                  :tree   (assoc tree :schema/uploadDate (now))}
          {:keys [iri] :as result} (ts/forest-action action)
          new-path (str "./static/images/" (name iri) ".jpg")]
    (log-action! user result)
    ;; TODO: Загружать файлы в СУБД -M 2024.10.19
    (fs/copyFile path new-path)
    (fs/rm path)
    iri))

(defn upload [{:keys [request-method] :as req} res raise]
  (case request-method
    :get
    (with-request-data req
      (-> (render-upload-form nil)
          (res/ok)
          (res/content-type "text/html")
          res))

    :post
    (-> (p/let [{:keys [body files user-data]} req
                [tree]   (parse-object body)
                tasks    (map #(do-upload-file
                                (-> % second :path)
                                tree
                                user-data)
                              files)
                entities (p/all tasks)]
          ;; TODO: нужно обрабатывать случаи, когда часть файлов загрузилась, а на
          ;;       одном из них возникла ошибка -M 2024.10.19
          (with-request-data req
            (-> (render-upload-form {:entities entities})
                (res/ok)
                (res/content-type "text/html")
                res)))
        (p/catch raise))

    (res (res/method-not-allowed "Method not allowed."))))

(def routes {:handler    #'upload
             :auth       true
             :middleware [[wrap-multipart-entries]]})
