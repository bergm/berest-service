(ns berest-service.berest.util
  (:require [clj-time.core :as ctc]))

(defn round [value & {:keys [digits] :or {digits 0}}]
  (let [factor (Math/pow 10 digits)]
    (-> value
      (* factor)
      Math/round
      (/ factor))))

(defn >=2digits [number]
  (if (< number 10) (str "0" number) (str number)))

(defn date-to-doy [day month & [year]]
  (.. (ctc/date-time (or year 2010) month day) getDayOfYear))

(defn doy-to-date [doy]
  (ctc/plus (ctc/date-time 2010 1 1) (ctc/days (dec doy))))

(def sum (partial reduce + 0))

(defn scalar-op [op scalar vector]
  (map #(op scalar %) vector))

(def s-add (partial scalar-op +))

(def s-mult (partial scalar-op *))

(defn dot-op [op vec1 vec2]
  (map #(op %1 %2) vec1 vec2))

(def dot-add (partial dot-op +))

(def dot-mult (partial dot-op *))
