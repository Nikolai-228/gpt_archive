(ns istu.archive.rdf.repr.time
  "Модуль для работы с временными сущностями из онтологии
  <https://www.w3.org/TR/owl-time>."
  (:require [istu.archive.rdf.model :as rdfm]
            #?(:cljs ["fast-printf" :refer [printf] :rename {printf format}])))

(def ^:private properties
  "Предикаты, указывающие на свойства момента времени в порядке от наиболее
  значимого к наименее значимому."
  [:time/year :time/month :time/day :time/hour :time/minute :time/second])

(defn encode-property [prop value]
  (case prop
    :time/year  {:type     :literal
                 :datatype :xsd/gYear
                 :value    (format "%s%04d"
                                   (if (neg? value) "-" "")
                                   (abs value))}
    :time/month {:type     :literal
                 :datatype :xsd/gMonth
                 :value    (format "--%02d" value)}
    :time/day   {:type     :literal
                 :datatype :xsd/gDay
                 :value    (format "---%02d" value)}

    (:time/hour :time/minute)
    {:type     :literal
     :datatype :xsd/nonNegativeInteger
     :value    (format "%02d" value)}

    :time/second
    {:type     :literal
     :datatype :xsd/decimal
     :value    (format "%s%f"
                       (if (< value 10) "0" "")
                       value)}))

(def ^:private precision-level
  {:time/unitYear   1
   :time/unitMonth  2
   :time/unitDay    3
   :time/unitHour   4
   :time/unitMinute 5
   :time/unitSecond 6})

(defn- maybe-deref [x]
  (if #?(:clj  (instance? clojure.lang.IDeref x)
         :cljs (satisfies? IDeref x))
    (deref x)
    x))

(defn instant->properties [instant]
  (let [dt        (maybe-deref (:time/inDateTime instant))
        precision (precision-level (:time/unitType dt))
        props     (take precision properties)]
    (into {} (map #(vector (keyword (name %))
                           (-> dt % rdfm/parse-literal))
                  props))))

(defn temporal->clj
  "Конвертирует RDF-описание ресурса, представляющего временную сущность, в
  структуру Clojure."
  [temporal]
  (if (:time/inDateTime temporal)
    (-> temporal instant->properties (assoc :type :instant))
    (let [start (:time/hasBeginning temporal)
          end   (:time/hasEnd       temporal)]
      {:type  :interval
       :start (some-> start instant->properties)
       :end   (some-> end   instant->properties)})))

(defmulti
  ^{:doc "Конвертирует структуру Clojure, представляющую временную
  сущность, в соответствующее RDF-дерево."}
  clj->temporal :type)

(defmethod clj->temporal :instant [props]
  (let [get-prop  #(some->> (get props (keyword (name %)))
                            (encode-property %)
                            (vector %))
        props'    (take-while some? (map get-prop properties))
        precision (count props')
        unit      (nth (keys precision-level) (dec precision))]
    {:type            :tree
     :rdf/type        :time/Instant
     :time/inDateTime (into {:type          :tree
                             :rdf/type      :time/DateTimeDescription
                             :time/unitType unit}
                            props')}))

(defmethod clj->temporal :interval [{:keys [start end]}]
  {:type              :tree
   :rdf/type          :time/Interval
   :time/hasBeginning (some-> start (assoc :type :instant) clj->temporal)
   :time/hasEnd       (some-> end   (assoc :type :instant) clj->temporal)})

(defn properties->iso-string [props]
  (let [{:keys [year month day hour minute second]
         :or   {year   0
                month  1
                day    1
                hour   0
                minute 0
                second 0.0}} props]
    (format "%s%04d-%02d-%02dT%02d:%02d:%s%fZ"
            (if (neg? year) "-" "") (abs year)
            month
            day
            hour
            minute
            (if (< second 10) "0" "")
            second)))

(def ^:private iso-pattern
  #"(-?\d{4,})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2}(.\d+)?)Z")

(defn iso-string->properties
  [iso & {:keys [unit], :or {unit :time/unitSecond}}]
  (if-let [[_ year month day hour minute second] (re-matches iso-pattern iso)]
    (let [props     {:year   (parse-long year)
                     :month  (parse-long month)
                     :day    (parse-long day)
                     :hour   (parse-long hour)
                     :minute (parse-long minute)
                     :second (parse-double second)}
          precision (precision-level unit)]
      (into {} (take precision props)))
    (throw (ex-info (str "Invalid ISO datetime string: " iso) {}))))
