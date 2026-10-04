(ns istu.archive.views.mark-form
  (:require [istu.archive.views.components :refer [autocomplete-field]]
            [istu.archive.views.layout :as layout]))

(defn render-mark-form [{:keys [return id]}]
  (layout/app
   [:main.container-lg
    [:form#form {:action return :method "post"}
     [:input {:type "hidden" :name "action" :value "add-depiction"}]
     [:div.photo-wrapper {:data-selection true
                          :style "max-width:800px;"}
      [:img.uninteractive {:src (str "/images/" id ".jpg")
                           :alt ""
                           :data-image-convert true}]
      [:svg.uninteractive.photo-overlay
       {:data-selection-area true
        :viewBox "0 0 100 100"
        :preserveAspectRatio "none"
        :style "display:none"}
       [:polygon {:fill-rule "evenodd"
                  :fill "rgba(0,0,0,0.5)"}]]
      (for [name ["x" "y" "width" "height"]]
        [:input {:type "hidden" :name name :required true}])]
     [:div.mb-3
      [:label.form-label {:for "object-field"} "Объект:"]
      (autocomplete-field :target :item
                          :name   "object"
                          :data   {:suggest-new-item true})]
     [:div.form-check
      [:input#guest-check.form-check-input {:type "checkbox" :name "guest"}]
      [:label.form-check-label {:for "guest-check"} "Гость"]]
     [:input.btn.btn-success.container-fluid.mt-4
      {:type "submit" :value "Отметить"}]]]
   [:script {:src "/js/auto.js"}]
   [:script {:src "/js/mark-on-photo.js"}]
   [:script {:src "/js/convert.js"}]))
