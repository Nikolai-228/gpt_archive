(ns istu.archive.routes.sparql
  (:require [hiccups.runtime :as hiccupsrt]
            [istu.archive.macros.request :refer [with-request-data]]
            [istu.archive.views.sparql :as sparql]
            [istu.archive.services.triplestore :as ts]
            [macchiato.middleware.params :refer [wrap-params]]
            [macchiato.util.response :as res]
            [promesa.core :as p]))

(defn sparql
  [{{:strs [q]} :query-params, {:strs [hx-request]} :headers, :as req}
   res raise]
  (if q
    (-> (p/let [start (system-time)
                data  (ts/get-bindings q)
                end   (system-time)
                opts  {:query q
                       :data data
                       :elapsed (- end start)}]
          (-> (with-request-data req
                (if hx-request
                  (hiccupsrt/render-html (sparql/render-results opts))
                  (sparql/render-sparql-form opts)))
              (res/ok)
              (res/content-type "text/html")
              res))
        (p/catch (fn [ex]
                   (let [status (or (-> ex ex-data :http-status) 500)
                         msg    (ex-message ex)]
                     (res
                      (with-request-data req
                        (if hx-request
                          {:status  status
                           :headers {"Content-Type" "text/plain"}
                           :body    msg}
                          {:status  status
                           :headers {"Content-Type" "text/html"}
                           :body    (sparql/render-sparql-form
                                     {:query q, :error msg})})))))))
    ;; if-not q
    (-> (with-request-data req
          (sparql/render-sparql-form {}))
        (res/ok)
        (res/content-type "text/html")
        res)))

(def routes {:get        #'sparql
             :middleware [[wrap-params]]})
