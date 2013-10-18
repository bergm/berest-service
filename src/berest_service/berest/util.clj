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


