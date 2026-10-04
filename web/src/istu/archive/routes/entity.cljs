(ns istu.archive.routes.entity
  (:require-macros [istu.archive.rdf.ns :as ns])
  (:require [istu.archive.api.form-data :refer [parse-action]]
            [istu.archive.macros.log :refer [log-action!]]
            [istu.archive.macros.request :refer [with-request-data]]
            [istu.archive.middleware :refer [wrap-form-entries]]
            [istu.archive.rdf.forest :as frs]
            [istu.archive.routes.core :refer [get-route-url]]
            [istu.archive.services.triplestore :as ts]
            [istu.archive.views.entity :refer [render-entity]]
            [istu.archive.views.mark-form :refer [render-mark-form]]
            [istu.archive.views.statement-form :refer [render-statement-form]]
            [macchiato.middleware.params :refer [wrap-params]]
            [macchiato.util.response :as res]
            [reitit.core :as r]
            [promesa.core :as p]))

(defn object
  [{{:keys [id]} :path-params, ::r/keys [router], :as req} res raise]
  (let [add-stmt-route (get-route-url router
                                      ::entity-add-stmt
                                      :path-params {:id id})
        mark-route     (get-route-url router
                                      ::entity-mark-object
                                      :path-params {:id id})]
    (-> (p/let [forest (ts/get-forest id)]
          (res
           (with-request-data req
             (if-not (empty? forest)
               (-> (frs/squirrel forest (keyword "iao" id))
                   (render-entity :add-stmt-route add-stmt-route
                                  :mark-route mark-route)
                   (res/ok)
                   (res/content-type "text/html"))
               (res/not-found "Not found.")))))
        (p/catch raise))))

(defn object-update
  [{{:keys [id]} :path-params, :keys [body], ::r/keys [router] :as req} res raise]
  (try
    (let [{:keys [user-data]} req
          subject (keyword "iao" id)
          action  (parse-action subject body)
          return  (get-route-url router
                                 ::entity-view
                                 :path-params {:id id})]
      (-> (p/let [result (ts/forest-action action)]
            (log-action! user-data result)
            (res (res/see-other return)))
          (p/catch raise)))
    (catch :default ex (raise ex))))

(defn- get-object-types [iri]
  (p/let [results (ts/get-bindings
                   (str "SELECT ?type WHERE { <" iri "> a ?type . }"))]
    (->> results :results :bindings (map #(-> % :type :value)))))

(defn object-add-statement-form
  [{{:keys [id]} :path-params, ::r/keys [router], :as req} res raise]
  (p/let [return    (get-route-url router
                                   ::entity-view
                                   :path-params {:id id})
          rdf-types (get-object-types (str (ns/expand :iao) id))]
    (with-request-data req
      (-> (render-statement-form {:return return
                                  :domain (first rdf-types)})
          (res/ok)
          (res/content-type "text/html")
          res))))

(defn object-mark-form
  [{{:keys [id]} :path-params, ::r/keys [router], :as req} res raise]
  (let [return (get-route-url router
                              ::entity-view
                              :path-params {:id id})]
    (with-request-data req
      (-> (render-mark-form {:return return, :id id})
          (res/ok)
          (res/content-type "text/html")
          res))))

(def routes
  [[""      {:name ::entity-view
             :get  object
             :post {:handler    object-update
                    :auth       {:role :editor}
                    :middleware [[wrap-form-entries]]}}]
   ["/add"  {:name       ::entity-add-stmt
             :get        object-add-statement-form
             :auth       {:role :editor}
             :middleware [[wrap-params]]}]
   ["/mark" {:name ::entity-mark-object
             :get  object-mark-form
             :auth {:role :editor}}]])
