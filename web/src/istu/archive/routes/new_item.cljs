(ns istu.archive.routes.new-item
  (:require [clojure.string :as str]
            [istu.archive.api.form-data :refer [try-canonical-iri]]
            [istu.archive.macros.log :refer [log-action!]]
            [istu.archive.macros.request :refer [with-request-data]]
            [istu.archive.rdf.ns :as ns]
            [istu.archive.services.triplestore :as ts]
            [istu.archive.util :as util]
            [istu.archive.views.new-item-form :refer [render-new-item-form]]
            [macchiato.middleware.params :refer [wrap-params]]
            [macchiato.util.response :as res]
            [promesa.core :as p]))

(defn new-item [{:keys [request-method params uri] :as req} res raise]
  (case request-method
    :get
    (with-request-data req
      (-> (render-new-item-form {:submit-href uri
                                 :label (get params "label")})
          (res/ok)
          (res/content-type "text/html")
          res))

    :post
    (-> (p/let [{:strs [lang type label description aliases]} params
                {:keys [user-data]} req
                type (try-canonical-iri type)
                lang (keyword lang)
                tree (cond-> {:type :tree, :rdf/type type}
                       (seq label)
                       (assoc-in [:label lang] label)

                       (seq description)
                       (assoc-in [:description lang] description)

                       (seq aliases)
                       (assoc-in [:alias lang] (-> aliases
                                                   (str/split #"\|")
                                                   set)))
                action {:action :create-entity, :tree tree}
                {:keys [iri] :as result} (ts/forest-action action)]
          (log-action! user-data result)
          (res (res/see-other (-> iri ns/kwi->iri util/fix-link))))
        (p/catch raise))

    (res (res/method-not-allowed "Method not allowed."))))

(def routes {:name       ::new-item
             :handler    #'new-item
             :auth       true
             :middleware [[wrap-params]]})
