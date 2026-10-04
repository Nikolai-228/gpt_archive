(ns istu.archive.views.entity
  (:require [clojure.core.match :refer [match]]
            [clojure.edn :as edn]
            [cognitect.transit :as transit]
            [istu.archive.i18n :as i18n :refer [*lang* lang->name]]
            [istu.archive.rdf.forest :as frs]
            [istu.archive.rdf.model :as rdfm]
            [istu.archive.rdf.ns :as ns]
            [istu.archive.rdf.repr.time :as repr-time]
            [istu.archive.util :as util]
            [istu.archive.views.layout :as layout]))

(def ^:dynamic *can-edit?* false)

(def undeletable-prop? #{:rdf/type :schema/uploadDate})

(defn button-delete [p o]
  [:form {:method "post"}
   [:input {:type "hidden" :name "action" :value "delete-stmt"}]
   [:input {:type "hidden" :name "predicate" :value (rdfm/full-iri p)}]
   [:input {:type  "hidden" :name "object"
            :value (let [w (transit/writer :json)]
                     (transit/write w (frs/node o)))}]
   [:button.btn.btn-danger.position-absolute.top-0.end-0.btn-sm "Удалить"]])

(defmethod rdfm/parse-literal :geom/shape [{:keys [value]}]
  (let [form (edn/read-string value)]
    (when-not (list? form)
      (throw (ex-info "Invalid geom:shape literal" {:value value})))
    (case (first form)
      rect (let [[x y width height] (rest form)]
              {:type   :rect
               :x      x
               :y      y
               :width  width
               :height height})
      (throw (ex-info "Unknown shape type" {:shape form})))))

(defn labels-table [data]
  [:table.table.table-bordered
   [:thead
    [:tr
     [:th "Язык"]
     [:th "Название"]
     [:th "Описание"]
     [:th "Также известно как"]]]
   [:tbody
    (for [lang (keys lang->name)]
      [:tr
       [:td (lang->name lang)]
       [:td (->> data :label       lang)]
       [:td (->> data :description lang)]
       [:td (->> data :alias       lang (interpose [:br]))]])]])

(defn node-title [sq]
  (or (-> @sq :label *lang*)
      (let [n (frs/node sq)]
        (if (qualified-keyword? n) (ns/kwi->qname n) n))))

(declare description)

(defn format-node [sq]
  (let [n (frs/node sq)]
    (cond
      (rdfm/literal? n)
      (list (:value n)
            (match [n]
              [{:language l}]
              [:sup "@" (or (lang->name l) (name l))]

              [{:datatype (dt :guard (complement rdfm/string-type?))}]
              [:sup "^^" (node-title (frs/jump sq dt))]

              :else nil))

      (->> @sq :rdf/type (some #{:time/Instant :time/Interval}))
      (i18n/format-temporal (repr-time/temporal->clj sq))

      (and (keyword? n) (#{":blank" "iav"} (namespace n)))
      (description sq)

      :else
      [:a {:href (-> n rdfm/full-iri util/fix-link)}
       (node-title sq)])))

(defn property [sq p os & {:keys [top-level?]}]
  [:div.property
   [:div.property__name
    [:a {:href (-> p rdfm/full-iri util/fix-link)}
     (node-title (frs/jump sq p))]]
   [:div.property__values
    (for [o (if (set? @os)
              os
              (list os))]
      [:div.property__value (format-node o)
       (when (and *can-edit?* top-level? (not (undeletable-prop? p)))
         (button-delete p o))])]])

(defn description [sq & {:keys [top-level?]}]
  (for [[p os] sq]
    (when (not (#{:ia/hasDepiction} p))
      (property sq p os :top-level? top-level?))))

(defn photo [sq & {:keys [mark-route]}]
  [:div.row
   [:div.photo-wrapper.col-lg-8
    [:img {:src (str "/images/" (-> sq frs/node name) ".jpg")
           :alt ""
           :data-image-convert true}]
    [:div.photo-overlay
     (for [depiction (:ia/hasDepiction sq)]
       (when-let [area (:ia/depictedArea depiction)]
         (let [{:keys [x y width height]} (rdfm/parse-literal @area)
               pc (fn [x] (-> x (* 100) (.toFixed 4)))]
           [:div.photo-overlay__depiction
            {:style (str "left:"   (pc x)      "%;"
                         "top:"    (pc y)      "%;"
                         "width:"  (pc width)  "%;"
                         "height:" (pc height) "%")}])))]]
   [:div.col
    [:h2 "На фотографии"]
    [:div.list-group
     (for [depiction (:ia/hasDepiction sq)]
       [:div.list-group-item
        [:div.fw-bold (format-node (:ia/depictedObject depiction))]
        (when-let [role (:ia/depictedAsRole depiction)]
          [:div.text-secondary (node-title role)])
        (when *can-edit?*
          (button-delete :ia/hasDepiction depiction))])]
    (when *can-edit?*
      [:a.btn.btn-success {:href mark-route} "Добавить отметку"])]])

(defn render-entity [sq & {:keys [add-stmt-route mark-route]}]
  (binding [layout/*title* (or (-> @sq :label *lang*)
                               (-> sq frs/node ns/kwi->qname))
            *can-edit?*    (contains? layout/*user-roles* :editor)]
    (layout/app
     [:main.container-lg
      (if-let [label (-> @sq :label *lang*)]
        [:h1 label]
        [:h1.text-secondary "Элемент без названия"])

      (if-let [desc (-> @sq :description *lang*)]
        [:p desc]
        [:p.text-secondary.fst-italic "Нет описания"])

      (when (-> @sq :rdf/type (contains? :schema/Photograph))
        (photo sq :mark-route mark-route))

      [:details
       [:summary "На других языках"]
       (labels-table @sq)]

      [:h2 "Утверждения"]
      (description sq :top-level? true)
      (when *can-edit?*
        [:a.btn.btn-success {:href add-stmt-route}
         "Добавить утверждение"])]
     [:script {:src "/js/convert.js"}])))
