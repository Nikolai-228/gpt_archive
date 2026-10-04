(ns istu.archive.views.sparql
  (:require [istu.archive.rdf.ns :as ns]
            [istu.archive.views.layout :as layout]
            [istu.archive.util :as util]))

(defn- sparql-results [data]
  [:table.table.overflow-x-scroll
   [:thead
    [:tr (for [v (-> data :head :vars)] [:th v])]]
   [:tbody
    (for [bind (-> data :results :bindings)]
      [:tr
       (for [v (-> data :head :vars)]
         [:td
          (let [{:keys [type value]} (-> v keyword bind)]
            (case type
              "uri"
              (let [kwi (ns/iri->kwi value)]
                [:a {:href (util/fix-link value)}
                 (if (and kwi (= "photo" v) (= "iao" (namespace kwi)))
                   [:img {:style "max-width:200px"
                          :loading "lazy"
                          :src (str "/images/" (name kwi) ".jpg")
                          :alt (ns/kwi->qname kwi)}]
                   (or (and kwi (ns/kwi->qname kwi))
                       value))])

              value))])])]])

(defn render-results [{:keys [data elapsed]}]
  [:section#query-results
   (when data
     (list
      [:div.alert "Запрос выполнен за " (-> elapsed (.toFixed 2)) " мс."]
      [:h2 "Результаты"]
      (sparql-results data)))])

(defn render-sparql-form [{:keys [query error] :as opts}]
  (layout/app
   [:main.container-lg
    [:h1 "Запрос SPARQL"]
    [:form#query-form {:method "get"}
     [:textarea.query-textbox.container-fluid
      {:name "q"
       :placeholder "(Введите запрос SPARQL или выберите из примеров выше.)"
       :cols "50" :rows "10"}
      query]
     [:input.btn.btn-primary.container-fluid
      {:type "submit"
       :value "Отправить"}]]
    (when error
      [:div.alert.alert-danger {:role "alert"}
       "Во время выполнения запроса произошла ошибка: " error "."])
    [:div#indicator]
    (render-results opts)]
   [:script {:src "/js/indicator.js"}]
   [:script {:src "/js/sparql.js"}]))
