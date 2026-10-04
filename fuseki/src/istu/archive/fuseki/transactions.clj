(ns istu.archive.fuseki.transactions
  "Макросы для работы с транзакциями в Apache Jena."
  (:import [java.util.function Supplier]
           [org.apache.jena.system Txn]))

(def ^:private valid-mode? #{:read :read-promote :write})

(defmacro with-transaction
  "Вычисляет заданные выражения в транзакции над набором данных в заданном
  режиме. См. <https://jena.apache.org/documentation/txn/transactions_api.html>.

  Возможные режимы включают:
  :read - только чтение
  :read-promote - чтение, при необходимости повышается до записи
  :write - запись"
  [dataset-sym mode & body]
  {:pre [(valid-mode? mode)]}
  (let [method-name (case mode
                      :read         'calculateRead
                      :read-promote 'calculate
                      :write        'calculateWrite)]
    `(. Txn (~method-name ~dataset-sym (reify Supplier
                                         (get [_#] ~@body))))))
