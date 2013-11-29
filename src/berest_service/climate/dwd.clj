(ns berest-service.climate.dwd
  (:require [clojure.math.numeric-tower :as nt]
            [clojure.java.io :as cjio]
            clojure.set
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clj-time
             [core :as ctc]
             [format :as ctf]]
            [berest-service.berest
             [datomic :as bd]
             [util :as bu]]
            [clojure-csv.core :as csv]
            [datomic.api :as d]
            [miner.ftp :as ftp]
            [instaparse.core :as insta]))


(defn parse-prognosis-data
  "parse a DWD prognosis data file and
  return {climate-station-id-1 {date-1 evaporation-1a date-2 evaporation-2 ...}
  climate-station-id-2 {date-1 evap-1 date-2 evap-2 ...} ... }"
  [data]
  (let [data* (str/split-lines data)
        data** (->> data* (drop 7 ,,,) (take 7))
        stations (str/split (first data**) #"\s+")
        evapos (drop 2 data**)
        evapos*
        (reduce (fn [acc evaporations]
                  (let [evaporations* (str/split evaporations #"\s+")
                        date (first evaporations*)
                        date* (ctf/parse (ctf/formatter "ddMMyyyy") date)]
                    (reduce (fn [a [station value]]
                              (assoc-in a [station date*] value))
                            acc (partition 2 (interleave (rest stations) (rest evaporations*)))))) {} evapos)
        ]
    evapos*))

(def data (slurp "FY60DWLA-20130527_0815.txt"))

(parse-prognosis-data data)


(def data* (str/split-lines data))

(def data** (->> data* (drop 7 ,,,) (take 7)))


(def stations (str/split (first data**) #"\s+"))

(def evapos (drop 2 data**))

(def evapos*
  (reduce (fn [acc evaporations]
            (let [evaporations* (str/split evaporations #"\s+")
                  date (first evaporations*)
                  date* (ctf/parse (ctf/formatter "ddMMyyyy") date)]
              (reduce (fn [a [station value]]
                        (assoc-in a [station date*] value))
                      acc (partition 2 (interleave (rest stations) (rest evaporations*)))))) {} evapos))


(partition 2 (interleave (rest stations) (rest (str/split (first evapos) #"\s+"))))



