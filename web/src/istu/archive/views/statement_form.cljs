(ns istu.archive.views.statement-form
  (:require [istu.archive.rdf.ns :as ns]
            [istu.archive.rdf.model :as rdfm]
            [istu.archive.views.components :refer [autocomplete-field]]
            [istu.archive.views.layout :as layout]))

(def ^:private value-kind-name
  {:item     "Элемент"
   :text     "Текст"
   :instant  "Момент времени"
   :interval "Интервал времени"
   :tree     "Структурированное значение"})

(defmulti render-value-input :kind)
(defmethod render-value-input :item
  [{:keys [rdf-range initial-suggest allow-iri]}]
  (list
   [:input {:type "hidden" :name "type" :value "iri"}]
   (autocomplete-field :target      :item
                       :name        "value"
                       :placeholder "Введите значение"
                       :data        {:type (rdfm/full-iri rdf-range)
                                     :initial-suggest initial-suggest
                                     :allow-iri allow-iri
                                     :suggest-new-item true})))
(defmethod render-value-input :text [_]
  (list
   [:input {:type "hidden" :name "type" :value "literal"}]
   [:div.row.g-1
    [:div.col-12.col-md-10.form-floating
     [:textarea.form-control {:name "value"
                              :required true
                              :placeholder "Содержание"}]
     [:label.d-block "Содержание"]]
    [:input {:type "hidden" :name "dt"}]
    [:div.col.form-floating
     [:input.form-control {:name "lang" :placeholder "Язык"}]
     [:label.d-block "Язык"]]]))
(defmethod render-value-input :tree [_]
  (list
   [:input {:type "hidden" :name "type" :value "tree"}]
   [:div.property
    [:div.property__name
     [:input {:type  "hidden"
              :name  "predicate"
              :value (ns/expand :rdf/type)}]
     [:span.form-control-plaintext "частный случай"]]
    [:div.property__values
     [:div.property__value
      [:input {:type "hidden" :name "type" :value "iri"}]
      (autocomplete-field :target      :type
                          :name        "value"
                          :placeholder "Тип")]]]
   [:button.btn.btn-success.btn-sm {:onclick "istu.archive.addProperty(event)"
                                    :type "button"}
    "Добавить свойство"]
   [:input {:type "hidden" :name "end-tree"}]))
(defmethod render-value-input :instant [_]
  (list
   [:input {:type "hidden" :name "type" :value "instant"}]
   [:div.row.g-0 {:data-input-instant true}
    [:input {:type "hidden" :name "parts" :value "6"}]
    (for [[key props] {:year   {:placeholder "год"
                                :pattern     "-?\\d{4,}"}
                       :month  {:placeholder "месяц"
                                :pattern     "[01]?[0-9]"}
                       :day    {:placeholder "день"
                                :pattern     "[0123]?[0-9]"}
                       :hour   {:placeholder "часов"
                                :pattern     "[0-2]?[0-9]"}
                       :minute {:placeholder "минут"
                                :pattern     "[0-5]?[0-9]"}
                       :second {:placeholder "секунд"
                                :pattern     "[0-5]?[0-9](\\.\\d+)?"}}]
      [:div.col-4.col-sm.form-floating
       [:input.form-control (merge {:name key
                                    :required (= key :year)
                                    :date-input-instant-part true}
                                   props)]
       [:label.d-block (:placeholder props)]])]))
(defmethod render-value-input :interval [_]
  (list
   [:input {:type "hidden" :name "type" :value "tree"}]
   [:input {:type "hidden" :name "predicate" :value (ns/expand :rdf/type)}]
   [:input {:type "hidden" :name "type" :value "iri"}]
   [:input {:type "hidden" :name "value" :value (ns/expand :time/Interval)}]
   [:div {:data-input-interval true}
    (for [field [{:title "Начало"
                  :prop  (ns/expand :time/hasBeginning)}
                 {:title "Конец"
                  :prop  (ns/expand :time/hasEnd)}]]
      [:div.card {:data-input-interval-part (:prop field)}
       [:div.card-header
        (:title field) " "
        [:button.btn.btn-success.btn-sm {:type "button"} "Добавить"]]])
    [:template {:data-input-interval-instant true}
     [:div (render-value-input {:kind :instant})]]]
   [:input {:type "hidden" :name "end-tree"}]))

(defn render-editor [{:keys [predicate domain kinds] :as opts}]
  [:div.property {:data-statement true}
   (autocomplete-field :class       "property__name"
                       :target      :predicate
                       :name        "predicate"
                       :value       predicate
                       :placeholder "Свойство"
                       :data        {:type domain})
   [:div.property__values
    [:div.property__value {:data-statement-value true}
     (when kinds
       (let [kind (or (:kind opts) (first kinds))
             opts (assoc opts :kind kind)]
         (list
          (when (seq (rest kinds))
            [:select.form-control.mb-1 {:data-statement-kind true
                                        :style "appearance:menulist"}
             (for [kind* kinds]
               [:option {:value    kind*
                         :selected (= kind kind*)}
                (value-kind-name kind*)])])
          (render-value-input opts))))]]])

(defn render-statement-form [{:keys [return] :as opts}]
  (layout/app
   [:main.container-lg
    [:form#form.mt-4 {:action return :method "post" :target "_parent" :autocomplete "off"}
     [:input {:type "hidden" :name "action" :value "add-stmt"}]
     (render-editor opts)
     [:input.btn.btn-success.mt-2
      {:type "submit", :value "Добавить"}]]]
    [:script {:src "/js/auto.js"}]
    [:script {:src "/js/statement-editor.js"}]))

