(ns istu.archive.macros.request
  #?(:cljs (:require-macros [istu.archive.macros.request])))

(defn- make-user-name [user]
  (when user
    (str (:given_name user) " " (:family_name user))))

(defmacro with-request-data [req & body]
  `(let [user# (:user-data ~req)]
     (binding [istu.archive.views.layout/*sitename-display*
               (istu.archive.services.postgres/get-ui-message "sitename-display")

               istu.archive.views.layout/*sitename-title*
               (istu.archive.services.postgres/get-ui-message "sitename-title")

               istu.archive.views.layout/*footer*
               (istu.archive.services.postgres/get-ui-message "footer")

               istu.archive.views.layout/*logged-in?*
               (some? user#)

               istu.archive.views.layout/*user-name*
               (make-user-name user#)

               istu.archive.views.layout/*user-roles*
               (:roles user#)]
       ~@body)))
