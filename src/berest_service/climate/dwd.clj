(ns berest-service.climate.dwd
  (:require [clojure.java.io :as cjio]
            [clojure.string :as str]
            [clj-time.core :as ctc]
            [clj-time.format :as ctf]
            [clj-time.coerce :as ctcoe]
            [berest-service.berest
             [datomic :as bd]
             [util :as bu]]
            [datomic.api :as d]
            #_[miner.ftp :as ftp]))

(defn parse-german-double [text]
  (.. java.text.NumberFormat (getInstance java.util.Locale/GERMAN) (parse text)))

(defn parse-prognosis-data
  "parse a DWD prognosis data file and
  return ({:station climate-station-id-1 :date date-1 :evap evaporation-1} {:station climate-station-id-1 :date date-2 :evap evaporation-2} ...)"
  [data]
  (let [data* (str/split-lines data)
        data** (->> data* (drop 7 ,,,) (take 7))
        stations (-> data** first (str/split ,,, #"\s+"))]
    (for [line (drop 2 data**)
          :let [line* (str/split line #"\s+")]
          [station evap] (map vector (rest stations) (rest line*))
          :let [date (first line*)
                date* (->> date (ctf/parse (ctf/formatter "ddMMyyyy") ,,,) ctcoe/to-date)]]
      {:weather-station/id station
       :weather-station/data {:weather-data/date date*
                              :weather-data/evaporation (parse-german-double evap)}})))

(comment "instarepl debugging code"

  (def pdata (slurp "FY60DWLA-20130527_0815.txt"))
  (def pdata* (parse-prognosis-data pdata))

  )


(defn parse-measured-data
  "parse ad DWD measured data file and return
  {climate-station-id-1 {date-1 {:precip precip-1 :evap evaporation-1} date-2 {:precip precip-2 :evap evaporation-2} ...}
  climate-station-id-2 {date-1 {:precip precip-1 :evap evap-1} date-2 {:precip precip-2 :evap evap-2} ...} ... }"
  [data]
  (let [data* (str/split-lines data)
        data** (->> data* (drop 6 ,,,) (take 3))
        stations (-> data** first (str/split ,,, #"\s+"))]
    (for [line (drop 2 data**)
          :let [line* (str/split line #"\s+")]
          [station precip evap] (map vector
                              (rest stations)
                              (take-nth 2 (rest line*))
                              (take-nth 2 (nnext line*)))
          :let [date (first line*)
                date* (->> date (ctf/parse (ctf/formatter "dd.MM.yyyy") ,,,) ctcoe/to-date)]]
      {:weather-station/id station
       :weather-station/data {:weather-data/date date*
                              :weather-data/precipitation (parse-german-double precip)
                              :weather-data/evaporation (parse-german-double evap)}})))

(defn as-transaction-fns [data]
  (map (fn [d]
         [:weather-station/add-data
          (:weather-station/id d)
          (-> d :weather-station/data :weather-data/evaporation)
          (-> d :weather-station/data :weather-data/precipitation)])
       data))

(comment "instarep debugging code"

  (def mdata (slurp "FY60DWLB-20130526_0815.txt"))
  (def mdata* (parse-measured-data mdata))

  (as-transaction-fns mdata*)

  )

(defn make-prognosis-filename [date]
  (str "FY60DWLA-" (ctf/unparse (ctf/formatter "yyyyMMdd") date) "_0815.txt"))

(defn make-measured-filename [date]
  (str "FY60DWLB-" (ctf/unparse (ctf/formatter "yyyyMMdd") date) "_0815.txt"))

(make-prognosis-filename (ctc/date-time 2013 6 3))

(comment "instarepl debug code"

  ;real ftp seams to be not necessary for just getting data (at least for anonymous access and co)
  (def t (ftp/with-ftp [client "ftp://anonymous:pwd@tran.zalf.de/pub/net/wetter"]
                       (ftp/client-get-stream client (make-prognosis-filename (ctc/date-time 2013 6 3)))))

  (clojure.java.io/reader t)

  )


(defn import-dwd-data-into-datomic [kind & [date]]
  (let [date* (or date (ctc/today))
        url "ftp://tran.zalf.de/pub/net/wetter/"
        url* (str url (case kind
                        :prognosis (make-prognosis-filename date*)
                        :measured (make-measured-filename date*)))
        data (slurp url*)
        transaction-data (case kind
                           :prognosis (parse-prognosis-data data)
                           :measured (parse-measured-data data))]
    transaction-data
    #_(d/transact (bd/current-db "berest") transaction-data)))


#_(import-dwd-data-into-datomic :prognosis (ctc/date-time 2013 6 6))


