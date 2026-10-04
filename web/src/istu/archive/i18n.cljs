(ns istu.archive.i18n
  "Работа с интернационализацией.")

;; TODO: Реализовать переключение языков
(def ^:dynamic *lang* :ru)

(def lang->name
  {:en  "английский"
   :ru  "русский"
   :udm "удмуртский"
   :tt  "татарский"
   :hu  "венгерский"
   :fi  "финский"
   :zh  "китайский"})

;;;; Форматирование дат

(defn- make-date [{:keys [year month day hour minute second]}]
  (js/Date. year (some-> month dec) (or day 1) hour minute second))

(defn- date-time-format-options [{:keys [year month day hour minute second]}]
  (cond-> {}
    year   (assoc :year "numeric")
    month  (assoc :month "long")
    day    (assoc :day "numeric")
    hour   (assoc :hour "2-digit")
    minute (assoc :minute "2-digit")
    second (assoc :second "2-digit")))

(defmulti format-temporal :type)
(defmethod format-temporal :instant [instant]
  (let [opts (date-time-format-options instant)
        dtf  (js/Intl.DateTimeFormat (name *lang*) (clj->js opts))
        date (make-date instant)]
    (.format dtf date)))
(defmethod format-temporal :interval [{:keys [start end]}]
  (if (and start end (= (keys start) (keys end)))
    (let [opts  (date-time-format-options start)
          dtf   (js/Intl.DateTimeFormat (name *lang*) (clj->js opts))
          date1 (make-date start)
          date2 (make-date end)]
      (.formatRange dtf date1 date2))

    (let [format1 (if start
                    (format-temporal (assoc start :type :instant))
                    "неизв.")
          format2 (if end
                    (format-temporal (assoc end :type :instant))
                    "н. в.")]
      (str format1 " — " format2))))
