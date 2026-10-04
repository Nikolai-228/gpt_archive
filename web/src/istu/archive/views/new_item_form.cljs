(ns istu.archive.views.new-item-form
  (:require [istu.archive.rdf.ns :as ns]
            [istu.archive.i18n :refer [lang->name]]
            [istu.archive.views.components :refer [autocomplete-field]]
            [istu.archive.views.layout :as layout]))

(defn render-new-item-form [{:keys [submit-href label]}]
  (binding [layout/*title* "Создание нового элемента"]
    (layout/app
     [:main.container-lg
      [:h1 "Создание нового элемента"]
      [:p
       "Здесь Вы можете создать новый элемент в базе знаний. Если Вы хотите
       загрузить материал в виде медиа-файла, пожалуйста, воспользуйтесь
       формой " [:a {:href "/upload"} "загрузки материалов"] "."]
      [:p
       "Перед созданием нового элемента убедитесь, что его ещё нет в системе; для
       этого воспользуйтесь формой поиска в верхней части страницы."]
      [:p
       "Будет полезным указать для нового элемента название, описание, а также при
       необходимости альтернативные названия. Название должно начинаться с
       заглавной буквы " [:em "только в том случае"] ", если это имя собственное;
       описание должно представлять собой назывное предложение без точки в
       конце."]
      [:form {:method "post"
              :action submit-href
              :autocomplete :off}
       [:div.mb-3
        [:label.form-label {:for "type-field"} "Частный случай класса"]
        (autocomplete-field :target :type :name "type")
        [:div.form-text
         "Например, " [:a {:href (ns/expand :schema/Person)} "человек"]
         ", " [:a {:href (ns/expand :schema/Corporation)} "коммерческая организация"]
         "."]]
       [:div.mb-3
        [:label.form-label {:for "language-field"} "Язык"]
        [:input#language-field.form-control
         {:type "text"
          :name "lang"
          :list "languages"
          :value "ru"
          :required true}]
        [:datalist#languages
         (for [[lang title] lang->name]
           [:option {:value lang} title])]]
       [:div.mb-3
        [:label.form-label {:for "label-field"} "Название"]
        [:input#label-field.form-control {:type "text"
                                          :name "label"
                                          :value label}]]
       [:div.mb-3
        [:label.form-label {:for "description-field"} "Описание"]
        [:input#description-field.form-control {:type "text"
                                                :name "description"}]]
       [:div.mb-3
        [:label.form-label {:for "alias-field"} "Альтернативные названия"]
        [:input#alias-field.form-control {:type "text" :name "aliases"}]
        [:div.form-text
         "Альтернативные названия должны быть разделены вертикальной чертой,
         например: " [:code "ИжГТУ|ИМИ"]]]
       [:input.btn.btn-primary {:type "submit" :value "Создать"}]]
      [:script {:src "/js/auto.js"}]
      [:script
       "istu.archive.initAutocompleteRecursive(document)"]])))
