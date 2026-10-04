(ns istu.archive.rdf.forest
  "Функции создания и исследования графов. Граф представляется структурой
  следующего вида:

      {s {p #{o ...} ...} ...},

  где s — субъекты троек, p — предикаты, o — объекты. Эта структура может быть
  создана при помощи редукции множества троек функцией `add-triple`. По этой
  структуре можно проходить с помощью «белки» (см. `squirrel`).

  Некоторые предикаты обрабатываются особо, и им в соответствие ставится не
  множество, а едиинчное значение. Таблица, соответствующая s, также может
  содержать ключи, не являющиеся предикатами (напр., `:label`, `:description`,
  `:alias` указывают на таблицу соответствия меток языка и
  названий (описаний))."
  (:require [istu.archive.rdf.model :refer [literal? resource?]])
  #?(:clj (:import [clojure.lang MapEntry])))

(def ^:private conj-set (fnil conj #{}))

(def ^:private singleton-property?
  "Свойства, которые могут иметь только одно значение. Для них значения даются не
  в виде множества из одного элемента, а непосредственно."
  #{;; time
    :time/unitType :time/year :time/month :time/day :time/hour :time/minute
    :time/second :time/inDateTime :time/inXSDDateTime :time/hasBeginning
    :time/hasEnd

    ;; ia
    :ia/depictedObject :ia/depictedArea :ia/depictedAsRole

    ;; geom
    :geom/upperLeft :geom/bottomRight :geom/x :geom/y})

(def ^:private non-graph-key?
  "Ключи, которые не указывают на связи в графе. Доступ по этим ключам не приведёт
  к прохождению по графу."
  #{:label :description :alias :type :language :value :datatype :direction})

(defn- add-label-property [prop forest s o]
  {:pre [(literal? o)]}
  (assoc-in forest
            [s prop (or (:language o) :default)]
            (:value o)))

(defn add-triple
  "Добавляет данную тройку к лесу."
  [forest [s p o]]
  (cond
    (#{:schema/name :skos/prefLabel :rdfs/label} p)
    (add-label-property :label forest s o)

    (#{:schema/description} p)
    (add-label-property :description forest s o)

    ;; Есть чувство, что альтернативные метки должны храниться в какой-то
    ;; последовательности, но по факту на уровне базы данных у них никакого
    ;; порядка нет.
    (#{:skos/altLabel} p)
    (update-in forest
               [s :alias (or (:language o) :default)]
               conj-set (:value o))

    (singleton-property? p)
    (assoc-in forest [s p] o)

    :else
    (update-in forest [s p] conj-set o)))

(defn- jump-forest [data forest]
  (if (resource? data)
    (get forest data)
    data))

(defprotocol ISquirrelImpl
  (-squirrel-lookup-impl [sq k not-found])
  (-squirrel-seq-impl [sq]))

(deftype Squirrel [data forest path]
  ISquirrelImpl
  (-squirrel-lookup-impl [_sq k not-found]
    (cond
      (and (map? data)
           (not (literal? data))
           (not (non-graph-key? k)))
      (if-let [v (get data k)]
        (if (set? v)
          (Squirrel. v forest (conj path k))
          (Squirrel. (jump-forest v forest)
                     forest
                     (conj path k v)))
        not-found)

      :else not-found))

  (-squirrel-seq-impl [sq]
    (cond
      (and (map? data) (not (literal? data)))
      (->> data
           keys
           (filter (complement non-graph-key?))
           (map #(MapEntry. % (-squirrel-lookup-impl sq % nil) #?(:cljs nil)))
           seq)

      (set? data)
      (map #(Squirrel. (jump-forest % forest)
                       forest
                       (conj path %))
           data)

      :else nil))

  #?@(:clj
      [clojure.lang.IDeref
       (deref [sq] (.-data sq))

       clojure.lang.ILookup
       (valAt [sq k] (-squirrel-lookup-impl sq k nil))
       (valAt [sq k not-found] (-squirrel-lookup-impl sq k not-found))

       clojure.lang.Seqable
       (seq [sq] (-squirrel-seq-impl sq))]

      :cljs
      [IDeref
       (-deref [sq] (.-data sq))

       ILookup
       (-lookup [sq k] (-squirrel-lookup-impl sq k nil))
       (-lookup [sq k not-found] (-squirrel-lookup-impl sq k not-found))

       ISeqable
       (-seq [sq] (-squirrel-seq-impl sq))]))

(defn squirrel
  "Создаёт объект-«белку», позволяющий свободно перемещаться и исследовать деревья
  в лесу (пуаллясь коньыез шӧдытон). Белка начинает движение с узла `start`.

  Белка позволяет переходить по узлам леса с помощью обычного доступа по
  ключевому слову, а также осуществлять итерацию подобно обычным таблицам и
  множествам."
  [forest start]
  {:pre [(resource? start)]}
  (Squirrel. (jump-forest start forest) forest (list start)))

(defn jump
  "Перемещает белку на данный узел; предыдущая история перемещений не
  сохраняется."
  [^Squirrel sq start]
  (squirrel (.-forest sq) start))

(defn node
  "Возвращает узел, на котором находится белка в данный момент."
  [^Squirrel sq]
  (first (.-path sq)))

(defn triple
  "Возвращает семантическую тройку, на объекте которой находится белка в данный
  момент. Если такой тройки нет, возвращает nil."
  [^Squirrel sq]
  (if (set? @sq)
    ;; Указывает на дугу
    nil
    (loop [p (.-path sq)
           i 0
           t (list)]
      (cond
        (= i 3)    (vec t)
        (empty? p) nil
        :else      (recur (rest p) (inc i) (conj t (first p)))))))

(defn annotation
  "Перемещает белку на аннотацию тройки, на которой сейчас находится белка."
  [^Squirrel sq]
  (some->> sq triple (jump sq)))
