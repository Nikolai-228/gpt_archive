(ns istu.archive.views.login
  (:require [istu.archive.views.layout :as layout]))

(defn render-login [{:keys [error]}]
  (binding [layout/*title* "Вход"]
    (layout/app
     [:main.row.d-flex.justify-content-center
      [:div.card.p-5.col-12.col-sm-8.col-md-6.col-lg-4
       (when error
         [:div.alert.alert-danger error])
       [:h1 "Вход в систему"]
       [:form {:method "post"}
        [:div.mb-2
         [:label.form-label {:for "username"} "Имя пользователя"]
         [:input#username.form-control. {:type "text" :name "username"}]]
        [:div.mb-2
         [:label.form-label {:for "password"} "Пароль"]
         [:input#password.form-control {:type "password" :name "password"}]]
        [:input.btn.btn-primary.btn-block.mt-2 {:type "submit"
                                                :value "Войти"}]]]])))
