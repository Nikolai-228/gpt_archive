(ns istu.archive.views.index
  (:require [istu.archive.i18n :as i18n :refer [*lang*]]
            [istu.archive.rdf.ns :as ns]
            [istu.archive.rdf.repr.time :refer [iso-string->properties]]
            [istu.archive.util :as util]
            [istu.archive.views.layout :as layout]
            [istu.archive.views.components :refer [autocomplete-field
                                                   icon
                                                   raw-html]]))

(defn make-choice [name variants]
  [:select.form-select {:name name}
   [:option {:value ""} "(не учитывать)"]
   (for [[variant data] variants]
     [:option {:value variant} (-> data :label *lang*)])])

(defn render-gallery-filter [{:keys [vocabular]}]
  [:div.card
   [:div.card-header [:h2.fs-6.mb-0 "Фильтр"]]
   [:form#galleryFilter.card-body {:autocomplete :off}
    [:div.mb-3
     [:p.mb-2 "Дата съёмки"]
     [:div.input-group
      [:span.input-group-text "от"]
      [:input.form-control {:type "date"
                            :name "start-date"
                            :pattern "-?\\d{4,}-\\d{2}-\\d{2}"
                            :placeholder "гггг-мм-дд"}]]
     [:div.input-group
      [:span.input-group-text "по"]
      [:input.form-control {:type "date"
                            :name "end-date"
                            :pattern "-?\\d{4,}-\\d{2}-\\d{2}"
                            :placeholder "гггг-мм-дд"}]]]
    [:div.mb-3
     [:label.form-label "Событие"]
     (autocomplete-field :target   :item
                         :name     "event"
                         :data     {:type (ns/expand :schema/Event)}
                         :required false)]
    [:div.mb-3
     [:label.form-label "Место"]
     (autocomplete-field :target   :item
                         :name     "location"
                         :data     {:type (ns/expand :schema/Place)}
                         :required false)]
    [:div.mb-3
     [:label.form-label "Жанр фотографии"]
     (autocomplete-field :target   :item
                         :name     "genre"
                         :data     {:type (ns/expand :ia/Genre)
                                    :initial-suggest true}
                         :required false)]
    [:div.mb-3
     [:label.form-label "Сортировка"]
     [:select.form-select {:name "sort"}
      [:option {:value "recently-uploaded"} "Сначала недавно загруженные"]
      [:option {:value "long-ago-uploaded"} "Сначала давно загруженные"]
      [:option {:value "newest"} "Сначала новые"]
      [:option {:value "oldest"} "Сначала старые"]]]

    [:div#filtersContainer.mb-3
     [:h3.fs-6.heading-with-buttons
      [:span "Люди на фотографии"]
      [:button.btn.btn-success.p-1 {:type "button"
                                    :title "Добавить человека"
                                    :data-filter-add true}
       (icon "add-line")]]
     [:template {:data-filter-template true}
      [:div.card {:data-filter "person"}
       [:div.card-header.heading-with-buttons
        [:span {:data-filter-title true}]
        [:div
         [:button.btn.collapsible-button.p-1.me-2
          {:type "button"
           :title "Развернуть"}
          (icon "arrow-down-line")]
         [:button.btn.btn-danger.p-1
          {:type "button"
           :data-filter-remove true
           :title "Удалить"}
          (icon "delete-bin-line")]]]
       [:div.collapsible-content
        [:div.card-body
         [:select.form-select.mb-3 {:name             "person"
                                    :data-filter-kind true}
          [:option {:value "exact", :selected true} "Конкретный человек"]
          [:option {:value "match"} "Поиск по критериям"]]
         [:div {:data-filter-fields true}]]]]]
     [:template {:data-filter-template-kind "exact"}
      [:div {:data-filter-fields true}
       (autocomplete-field :target :item
                           :name   "item"
                           :data   {:type (ns/expand :schema/Person)}
                           :placeholder "Введите имя")]]
     [:template {:data-filter-template-kind "match"}
      [:div {:data-filter-fields true}
       [:div.mb-3
        [:input.form-control {:type "text"
                              :name "family-name"
                              :placeholder "Фамилия"}]
        [:input.form-control.mt-2 {:type "text"
                                   :name "given-name"
                                   :placeholder "Имя"}]
        [:input.form-control.mt-2 {:type "text"
                                   :name "patronymic-name"
                                   :placeholder "Отчество"}]]
       [:div.mb-3
        [:label.form-label "Пол"]
        (make-choice "gender" (:gender vocabular))]
       [:div.mb-3
        [:label.form-label "Место работы"]
        (autocomplete-field :target   :item
                            :name     "workplace"
                            :required false
                            :data     {:type (ns/expand :schema/Organization)})]
       [:div.mb-3
        [:label.form-label "Должность"]
        (autocomplete-field :target   :item
                            :name     "position"
                            :required false
                            :data     {:type (ns/expand :org/Role)})]
       [:div.mb-3
        [:label.form-label "Учёное звание"]
        (make-choice "academic-rank" (:academic-rank vocabular))]
       [:div.mb-3
        [:label.form-label "Учёная степень"]
        (autocomplete-field :target   :item
                            :name     "academic-degree"
                            :required false
                            :data     {:type (ns/expand :ia/AcademicDegree)})]
       [:div.mb-3
        [:label.form-label "Награда"]
        (autocomplete-field :target   :item
                            :name     "award"
                            :required false
                            :data     {:type (ns/expand :ia/Award)})]
       [:div
        [:label.form-label "Участник события"]
        (autocomplete-field :target   :item
                            :name     "participated"
                            :required false
                            :data     {:type (ns/expand :schema/Event)})]]]]
    [:input.btn.btn-primary.container-fluid {:type  "submit"
                                             :value "Поиск"}]]])

(defn- get-date [b]
  (when-let [iso (-> b :date :value)]
    (let [unit  (-> b :unit :value ns/iri->kwi)
          props (iso-string->properties iso :unit unit)]
      {:type  :instant
       :year  (:year props)
       :month (:month props)
       :day   (:day props)})))

(def ^:private pages-shown 5)

(defn render-pagination [{:keys [page total-pages page-href]}]
  (let [has-prev? (and (pos? total-pages) (> page 0))
        has-next? (> total-pages (inc page))
        left      (max (min (- page 2) (- total-pages pages-shown)) 0)
        right     (min (max (+ left pages-shown) (+ page 3)) total-pages)]
    [:nav.mt-4.overflow-x-scroll {:aria-label "Страницы результатов поиска"}
     [:ul.pagination
      [:li {:class (str "page-item" (when-not has-prev? " disabled"))}
       [:a.page-link {:href (when has-prev? (page-href (dec page)))}
        "Назад"]]
      (for [i (range left right)]
        (let [current? (= i page)]
          [:li {:class        (str "page-item" (when current? " active"))
                :aria-current (when current? "page")}
           [:a.page-link {:href (page-href i)} (inc i)]]))
      [:li {:class (str "page-item" (when-not has-next? " disabled"))}
       [:a.page-link {:href (when has-next? (page-href (inc page)))}
        "Дальше"]]]]))

(defn render-results [{:keys [bindings total-pages] :as opts}]
  [:div#query-results
   (if-let [bs (seq bindings)]
     (list
      [:div.row.row-cols-2.row-cols-sm-3.row-cols-xl-4.g-2
       (for [b bs]
         (let [iri  (-> b :photo :value)
               id   (-> iri ns/iri->kwi name)
               date (get-date b)]
           [:div.col
            [:a.gallery-photo.rounded.overflow-hidden
             {:href (util/fix-link iri)}
             [:img.gallery-photo__img {:loading "lazy"
                                       :src (str "/images/" id ".jpg")
                                       :alt ""
                                       :data-image-convert true}]
             (when date
               [:span.gallery-photo__date (i18n/format-temporal date)])]]))]
      (when (> total-pages 1)
        (render-pagination opts)))
     [:p "По Вашему запросу ничего не было найдено."])])

(defn render-index [{:keys [motd error] :as opts}]
  (layout/app
    [:main.container-fluid
     [:h1.visually-hidden "Главная страница"]
     (when motd (raw-html motd))
     (when layout/*user-name*
       [:p "Вы вошли в систему как " [:em layout/*user-name*] "."])
     [:h2 "Галерея"]
     (if error
       [:div.alert.alert-danger {:role "alert"}
        "Во время выполнения запроса произошла ошибка: " error "."]
       ;; if-not error
       [:div.row.align-items-start.overflow-hidden
        [:div.col-12.col-lg-4.col-xl-3
         (render-gallery-filter opts)]
        [:div.col.position-relative
         [:div#indicator.z-1.position-absolute.top-0.start-0.end-0]
         (render-results opts)]])]
    [:script {:src "/js/auto.js"}]
    [:script {:src "/js/indicator.js"}]
    [:script {:src "/js/convert.js"}]
    [:script {:src "/js/gallery.js"}]))
