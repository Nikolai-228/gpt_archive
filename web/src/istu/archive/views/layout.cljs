(ns istu.archive.views.layout
  (:require-macros [hiccups.core :refer [html5]])
  (:require [hiccups.runtime :as hiccupsrt]
            [istu.archive.config :as config]
            [istu.archive.i18n :refer [*lang*]]
            [istu.archive.views.components :refer [icon raw-html]]))

(def ^:dynamic *sitename-display* nil)
(def ^:dynamic *sitename-title* nil)
(def ^:dynamic *footer* nil)

(def ^:dynamic *title* nil)
(def ^:dynamic *search* nil)

(def ^:dynamic *logged-in?* false)
(def ^:dynamic *user-name*  nil)
(def ^:dynamic *user-roles* #{})

(def ^:dynamic *development* (not (:archive-production @config/env)))

(def ^:private common-links
  {"/sparql"           "SPARQL"
   "/docs/manual.html" "Руководство"})
(def ^:private links-by-role
  {:editor
   {"/new-item" "Новый элемент"
    "/upload"   "Загрузка материалов"}})

(defn- get-header-links []
  (merge common-links
         (apply #'merge (map links-by-role *user-roles*))
         (if *logged-in?*
           {"/logout" "Выход"}
           {"/login"  "Вход"})))

(defn app [& body]
  (html5 {:lang *lang*
          :xmlns:xlink "http://www.w3.org/1999/xlink"}
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:meta {:name "color-scheme" :content "light dark"}]
     [:title (if *title*
               (str *title* " | " *sitename-title*)
               *sitename-title*)]
     [:link {:rel "stylesheet" :href "/index.css"}]
     [:script {:src "/js/common.js"}]
     [:script {:src "/js/theme.js"}]]
    [:body.for-footer
     [:header {:class (str "bg-primary mb-4"
                           (when *development* " header__dev"))
               :data-bs-theme "dark"}
      [:nav.navbar.navbar-expand-md.ms-4.me-4
       [:a.navbar-brand.p-0.fs-6.fw-semibold {:href "/"}
        [:div.d-flex.align-items-center
         [:img {:src "/assets/logo.svg" :alt "" :height "56"}]
         [:div.ms-2 (raw-html *sitename-display*)]]]
       [:ul.navbar-nav.me-auto
        (for [link (get-header-links)]
          [:li.nav-item
           [:a.nav-link {:href (key link)} (val link)]])]
       [:form#search-form.d-flex {:action "/search"
                                  :method :get
                                  :autocomplete :off
                                  :data-bs-theme "light"}
        [:input.form-control {:name "q"
                              :type "search"
                              :aria-label "Поиск по архиву"
                              :required true
                              :value *search*}]
        [:button.btn.btn-primary (icon "search-line")]]]]
     body
     [:footer.p-3.text-center.mt-auto (raw-html *footer*)]]))
