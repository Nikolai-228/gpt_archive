(ns istu.archive.fuseki.base32
  "Вспомогательные функции для кодирования в Base-32.")

(def alphabet "abcdefghijklmnopqrstuvwxyz234567")

(defn digit [n] (nth alphabet n))

(defn encode-number
  "Кодирует заданное число в Base-32. Возвращает последовательность
  цифр (символов), начиная с наименее значащих."
  [x]
  (->> x
       (iterate #(unsigned-bit-shift-right % 5))
       (map #(digit (bit-and % 2r11111)))))
