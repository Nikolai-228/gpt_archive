(ns istu.archive.views.upload
  (:require [istu.archive.rdf.ns :as ns]
            [istu.archive.util :as util]
            [istu.archive.views.layout :as layout]))

(defn render-upload-result [{:keys [entities]}]
  (when entities
    [:div.alert.alert-success {:role "alert"}
     [:p "Материалы были успешно загружены."]
     [:ul.p-0.mb-0 {:style "list-style:none"}
      (for [entity entities]
        [:li.d-inline-block.mb-4.me-4
         [:a {:href (-> entity ns/kwi->iri util/fix-link)}
          [:img {:alt (ns/kwi->qname entity)
                 :src (str "/images/" (name entity) ".jpg")
                 :style "max-width:200px"}]]])]]))

(defn render-upload-form [opts]
  (binding [layout/*title* "Загрузка материалов"]
    (layout/app
     [:main.container-lg
      (render-upload-result opts)
      [:h1 "Загрузка материалов"]
      [:p
       "Выберите файлы, которые Вы хотите загрузить в коллекцию. Ниже вы можете
       указать общие свойства, которые будут добавлены к каждому из загруженных
       материалов."]
      [:form {:method "post" :enctype "multipart/form-data"}
       [:input.form-control {:type "file"
                             :name "files"
                             :required true
                             :multiple true}]
       [:input {:type "hidden" :name "type" :value "tree"}]
       [:div.property.mt-2
        [:div.property__name
         [:input {:type  "hidden"
                  :name  "predicate"
                  :value (ns/expand :rdf/type)}]
         [:span.form-control-plaintext "частный случай"]]
        [:div.property__values
         [:div.property__value
          [:input {:type "hidden" :name "type" :value "iri"}]
          [:input {:type  "hidden"
                   :name  "value"
                   :value (ns/expand :schema/Photograph)}]
          [:span.form-control-plaintext "фотография"]]]]
       [:button.btn.btn-success.mt-2
        {:type "button"
         :onclick "istu.archive.addProperty(event)"
         :data-statement-opts
         (js/JSON.stringify #js {:domain (ns/expand :schema/Photograph)})}
        "Добавить утверждение"]
       [:input {:type "hidden" :name "end-tree"}]
       [:input.btn.btn-primary.ms-4.mt-2 {:type "submit" :value "Отправить"}]]]
     [:script {:src "/js/auto.js"}]
     [:script {:src "/js/statement-editor.js"}])))
