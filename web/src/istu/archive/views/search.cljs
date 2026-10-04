(ns istu.archive.views.search
  (:require [istu.archive.rdf.ns :as ns]
            [istu.archive.views.layout :as layout]
            [istu.archive.util :as util]))

(defn render-result [{:keys [entity name desc match]}]
  [:section
   [:h3
    [:a {:href (-> entity :value util/fix-link)}
     (or (-> name :value)
         (-> entity :value ns/iri->kwi ns/kwi->qname))]
    (when-not (= (:value name) (:value match))
      (list " (" (-> match :value) ")"))]
   (when desc
     [:p (-> desc :value)])])

(defn render-search [{:keys [search elapsed bindings]}]
  (binding [layout/*title* (str "Результаты поиска «" search "»")]
    (layout/app
     [:main.container-lg
      [:h2 "Результаты поиска"]
      [:p
       "Найдено " (count bindings) " результатов за " (.toFixed elapsed 2)
       " мс."]
      [:ul
       (for [bind bindings]
         [:li (render-result bind)])]])))
