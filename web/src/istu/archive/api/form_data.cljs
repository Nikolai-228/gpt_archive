(ns istu.archive.api.form-data
  (:require [clojure.string :as str]
            [cognitect.transit :as transit]
            [istu.archive.rdf.ns :as ns]
            [istu.archive.rdf.repr.time :as repr-time]))

(defn- coerce-to-nil [s] (if (empty? s) nil s))

(defn try-canonical-iri [s]
  (let [[prefix remaining] (str/split s #":" 2)]
    (if (#{"http" "https"} prefix)
      s
      (if (ns/prefix-registered? prefix)
        (keyword prefix remaining)
        (throw (ex-info (str "Unknown prefix: " prefix) {}))))))

(defn parse-object
  "Разбирает последовательность пар «ключ—значение», полученных из HTML формы, в
  объект утверждения, который можно добавить в граф сервисом forest.

  Всегда первым в последовательности должен идти параметр type, принимающий
  следующие значения:
  iri     - указывает на именованный узел, определяемый IRI
  literal - указывает на литерал
  instant - указывает на момент времени
  tree    - указывает на поддерево.

  Для type=iri дальше должен следовать параметр value, который и указывает IRI
  ресурса.

  Для type=literal далее должны в любом порядке следовать параметры value, lang,
  dt, которые определяют соответственно лексическое содержание литерала, метку
  языка и тип данных.

  Для type=instant далее в начале должен следовать параметр parts, задающий
  количество частей (год, месяц и т.д.), а затем в любом порядке должны
  следовать численные параметры year, month, day, hour, minute, second; общее их
  количество должно составлять parts. Все параметры, кроме year, могут быть
  пустыми.

  Для type=tree далее должны очерёдно следовать параметры predicate, указывающие
  IRI предиката, и объекты, которые разбираются с помощью этой же функции
  рекурсивно. В конце должен стоять параметр end-tree.

  Функция возвращает вектор из разобранного объекта и последовательности
  оставшихся пар «ключ—значение»."
  [pairs]
  (let [[[k type] & pairs] pairs]
    (when-not (= "type" k)
      (throw (ex-info (str "Expected type parameter, found: " k) {})))
    (case type
      "iri"
      (let [[[k value] & pairs] pairs]
        (when-not (= k "value")
          (throw (ex-info (str "Expected value parameter, found: " k) {})))
        (when (empty? value)
          (throw (ex-info "No value provided" {})))
        [(try-canonical-iri value) pairs])

      "literal"
      (let [{:strs [value dt lang]} (into {} (take 3 pairs))
            pairs (nthrest pairs 3)]
        (when (empty? value)
          (throw (ex-info "No value provided" {})))
        ;; NOTE: Здесь это не валидируется, но при задании одновременно и lang,
        ;;       и dt — тип данных игнорируется.
        [{:type     :literal
          :value    value
          :datatype (some-> dt coerce-to-nil try-canonical-iri)
          :language (some-> lang coerce-to-nil keyword)}
         pairs])

      "tree"
      (loop [pairs pairs
             tree  {:type :tree}]
        (let [[[k v] & pairs] pairs]
          (case k
            "end-tree"
            [tree pairs]

            "predicate"
            (let [predicate      (try-canonical-iri v)
                  [object pairs] (parse-object pairs)]
              (recur pairs (update tree predicate (fnil conj #{}) object)))

            (throw (ex-info "Unexpected EOD" {})))))

      "instant"
      (let [[[_ num-parts] & pairs] pairs
            num-parts (parse-long num-parts)
            fields (into {} (take num-parts pairs))
            {:strs [year month day hour minute second]} fields
            pairs (nthrest pairs num-parts)]
        (when (empty? year)
          (throw (ex-info "At least the year should be specified" {})))
        (let [instant {:type   :instant
                       ;; TODO: when not a number is supplied, the error is
                       ;;       “Index out of bounds”
                       :year   (some-> year   coerce-to-nil parse-long)
                       :month  (some-> month  coerce-to-nil parse-long)
                       :day    (some-> day    coerce-to-nil parse-long)
                       :hour   (some-> hour   coerce-to-nil parse-long)
                       :minute (some-> minute coerce-to-nil parse-long)
                       :second (some-> second coerce-to-nil parse-double)}]
          [(repr-time/clj->temporal instant) pairs]))

      (throw (ex-info (str "Unexpected type: " type) {})))))

(defn parse-action [s pairs]
  (let [[[k action] & pairs] pairs]
    (when-not (= "action" k)
      (throw (ex-info (str "Expected action, found: " k) {})))

    (case action
      "delete-stmt"
      (let [{:strs [predicate object]} (into {} (take 2 pairs))]
        (when (some empty? (list predicate object))
          (throw (ex-info "predicate= and object= should be given" {})))
        {:action    :delete-stmt
         :subject   s
         :predicate predicate
         :object    (let [r (transit/reader :json)]
                      (transit/read r object))})

      "add-stmt"
      (let [[[k predicate] & pairs] pairs]
        (when-not (= "predicate" k)
          (throw (ex-info (str "Expected predicate, found: " k) {})))
        (when (empty? predicate)
          (throw (ex-info "No predicate is given." {})))
        {:action    :add-stmt
         :subject   s
         :predicate (try-canonical-iri predicate)
         :object    (first (parse-object pairs))})

      "add-depiction"
      (let [{:strs [object guest x y width height]} (into {} pairs)]
        (when (some empty? (list object x y width height))
          (throw (ex-info "object, x, y, width, height should be given" {})))
        (let [[x y width height] (map parse-double (list x y width height))
              area {:type     :literal
                    :datatype :geom/shape
                    :value    (pr-str (list 'rect x y width height))}]
          {:action    :add-stmt
           :subject   s
           :predicate :ia/hasDepiction
           :object    {:type              :tree
                       :rdf/type          :ia/Depiction
                       :ia/depictedObject (try-canonical-iri object)
                       :ia/depictedArea   area
                       :ia/depictedAsRole (when guest :ia/Guest)}}))

      (throw (ex-info (str "Invalid action: " action)
                      {:params pairs})))))
