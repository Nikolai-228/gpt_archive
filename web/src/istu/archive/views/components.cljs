(ns istu.archive.views.components)

(defn raw-html [html]
  [:div {:dangerously-set-inner-HTML {:__html html}}])

(defn icon [name]
  (let [href (str "/assets/icons.svg#" name)]
    [:svg.icon
     [:use {:xlink:href  href}]]))

(def ^:private autocomplete-target? #{:item :type :predicate})

(defn autocomplete-field
  "Поле с автоматическими предложениями при вводе. Информация о возможных
  вариантах ввода получается из хранилища троек. Для работы необходимо
  исполнение JavaScript-кода в браузере.

  Параметр :target должен быть одной из следующих ключевых слов:
  :item      - значением поля будет IRI некоторого элемента
  :type      - значением поля будет IRI некоторого типа (класса)
  :predicate - значением поля будет IRI некоторого предиката.

  По параметру :data можно передать таблицу, дополнительно определяющие логику
  предложений. Доступны следующие параметры:
  :type             - в случае :target :item подсказывает элементы указанного
                      типа, в случае :target :predicate подсказывает свойства,
                      применимые к элементу указанного типа. Необходимо указать
                      IRI в виде строки
  :initial-suggest  - делать ли предложения при первом фокусировании поля
  :allow-iri        - в случае :target :item разрешает произвольные IRI
  :suggest-new-item - в случае :target :item при отсутствии результатов поиска
                      предлагается создать элемент с указанным названием"
  [& {:keys [target name data placeholder required value class]
      :or   {required true}}]
  {:pre [(autocomplete-target? target)]}
  (let [data-json (when data
                    (js/JSON.stringify (clj->js data)))]
    [:div {:class class
           :data-autocomplete      target
           :data-autocomplete-data data-json}
     [:input {:name     name
              :required required
              :style    "display:none"
              :value    value
              :data-autocomplete-inner true}]
     [:input#object-field.form-control
      {:placeholder placeholder
       :disabled    true
       :required    required
       :data-autocomplete-visible true}]]))
