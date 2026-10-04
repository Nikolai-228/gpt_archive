(ns istu.archive.views.suggest
  (:require-macros [hiccups.core :refer [html]])
  (:require [clojure.string :as str]
            [istu.archive.rdf.ns :as ns]))

(defn render-create-new-item [{:keys [href query]}]
  (html
    [:ul.autocomplete-items.list-group
     [:li.list-group-item
      [:a.fst-italic {:href href :target "_blank"}
       "Не нашли то, что искали? Создать новый элемент «" query "»"]]]))

(defn render-suggestions [bindings]
  (html
    [:ul.autocomplete-items.list-group
     (for [{:keys [entity label description match]} bindings]
       (let [label' (or (-> label :value)
                        (-> entity :value ns/iri->kwi ns/kwi->qname))
             match' (when-let [{:keys [value]} match]
                      (let [http  (str/starts-with? value "http://")
                            https (str/starts-with? value "https://")]
                        (or (and (or http https)
                                 (-> value ns/iri->kwi ns/kwi->qname))
                            value)))]
         [:li.list-group-item {:onclick "istu.archive.selectSuggestion(this);return false"
                               :data-autocomplete-label label'
                               :data-autocomplete-value (-> entity :value)}
          [:a {:href (-> entity :value)}
           [:div.fw-bold
            label'
            (when (and match' (not= label' match'))
              (list " (" match' ")"))]
           [:div (:value description)]]]))]))
