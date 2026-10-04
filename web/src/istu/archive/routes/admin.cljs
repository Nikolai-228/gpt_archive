(ns istu.archive.routes.admin
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [istu.archive.macros.log :refer [log-action!]]
            [istu.archive.macros.request :refer [with-request-data]]
            [istu.archive.middleware :refer [wrap-read-body]]
            [istu.archive.services.postgres :as pg]
            [istu.archive.services.triplestore :as ts]
            [istu.archive.views.layout :as layout]
            [macchiato.middleware.params :refer [wrap-params]]
            [macchiato.util.response :as res]
            [promesa.core :as p]))

(defn render-admin-root [{:keys [messages]}]
  (layout/app
   [:main.container-lg
    [:h1 "Панель администрирования"]
    [:section
     [:p [:a {:href "/admin/forest"} "Сервис forest"]]
     [:h2 "Редактирование сообщений интерфейса"]
     [:ul
      (for [msg messages]
        [:li [:a {:href (str "/admin/msg/" msg)} msg]])]]]))

(defn render-edit-message [{:keys [id content success error]}]
  (layout/app
   [:main.container-lg
    [:h1 "Редактирование сообщения " id]
    (when success
      [:div.alert.alert-success {:role "alert"} "Успех"])
    (when error
      [:div.alert.alert-danger {:role "alert"} error])
    [:form {:autocomplete "off"
            :method       "post"}
     [:textarea.query-textbox.container-fluid {:name     "content"
                                               :required true
                                               :cols     "50"
                                               :rows     "10"}
      content]
     [:input.btn.btn-primary.container-fluid {:type  "submit"
                                              :value "Обновить"}]]]))

(defn render-forest-repl []
  (layout/app
   [:main.container-lg
    [:h1 "Сервис forest"]
    [:form#repl {:autocomplete "off"
                 :method       "post"}]
    [:button#clear.btn.btn-secondary "Очистить историю"]
    [:template#repl-record
     [:div.d-flex.align-items-start
      [:div.font-monospace.ps-2.pe-2.pt-1
       {:data-repl-marker true}
       ">"]
      [:pre.m-0.p-1.fs-6 {:data-repl-content true
                          :style "white-space:pre-wrap"}]]]
    [:template#repl-input
     [:div.d-flex.align-items-start
      [:div.align-self-stretch.bg-primary-subtle.font-monospace.ps-2.pe-2.pt-1 ">"]
      [:textarea.query-textbox.flex-grow-1 {:name     "input"
                                            :required true
                                            :cols     "50"
                                            :rows     "3"}]
      [:input.btn.btn-primary {:type  "submit"
                               :value "Выполнить"}]]]]
   [:script {:src "/js/repl.js"}]))

(defn admin-root [req res raise]
  (let [messages (pg/ui-messages-names)
        opts     {:messages messages}]
    (-> (with-request-data req
          (render-admin-root opts))
        (res/ok)
        (res/content-type "text/html")
        res)))

(defn forest-repl-ui [req res raise]
  (-> (with-request-data req
        (render-forest-repl))
      (res/ok)
      (res/content-type "text/html")
      res))

(defn- read-qname [s]
  (let [[prefix local] (str/split s #":")]
    (keyword prefix local)))

(defn handle-forest [{:keys [body user-data] :as req} res raise]
  (-> (p/let [input (str body)
              edn   (edn/read-string
                     {:readers {'rdf/qname read-qname}}
                     input)]
        (when-not (list? edn)
          (throw (ex-info "Input is not a list" {})))
        (case (first edn)
          get
          (p/let [id     (name (second edn))
                  forest (ts/get-forest id)]
            (-> (res/ok (with-out-str (pp/pprint forest)))
                (res/content-type "text/plain")
                res))

          execute
          (p/let [action (second edn)
                  result (ts/forest-action action)]
            (log-action! user-data result)
            (-> (res/ok (with-out-str (pp/pprint result)))
                (res/content-type "text/plain")
                res))

          print
          (let [args (rest edn)]
            (-> (res/ok (apply print-str args))
                (res/content-type "text/plain")
                res))

          :else
          (throw (ex-info (str "Unknown command: " (first edn)) {}))))
      (p/catch #(res {:status  (or (-> % ex-data :http-status) 500)
                      :headers {"Content-Type" "text/plain"}
                      :body    (-> % ex-message)}))))

(defn message-edit
  [{:keys [request-method], {:keys [id]} :path-params, :as req} res raise]
  (if-let [content (pg/get-ui-message id)]
    (case request-method
      :get
      (-> (with-request-data req
            (render-edit-message {:id id, :content content}))
          (res/ok)
          (res/content-type "text/html")
          res)

      :post
      (-> (p/let [content (-> req :form-params (get "content"))
                  _       (pg/update-ui-message id content)
                  opts    {:success true
                           :id      id
                           :content content}]
            (-> (with-request-data req (render-edit-message opts))
                (res/ok)
                (res/content-type "text/html")
                res))
          (p/catch #(-> (with-request-data req
                          (render-edit-message {:id      id
                                                :content content
                                                :error   (ex-message %)}))
                        (res/internal-server-error)
                        (res/content-type "text/html")
                        res)))

      (res (res/method-not-allowed "Method not allowed.")))
    (res (res/not-found "Not found."))))

(def routes
  ["/" {:auth {:role :admin}}
   [""        {:get #'admin-root}]
   ["forest"  {:get        #'forest-repl-ui
               :post       {:handler #'handle-forest
                            :middleware [[wrap-read-body]]}}]
   ["msg/:id" {:handler    #'message-edit
               :middleware [[wrap-params]]}]])
