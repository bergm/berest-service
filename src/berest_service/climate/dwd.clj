(ns berest-service.climate.dwd
  (:require [clojure.java.io :as cjio]
            [clojure.string :as str]
            [clj-time.core :as ctc]
            [clj-time.format :as ctf]
            [clj-time.coerce :as ctcoe]
            [berest-service.berest.datomic :as bd]
            [berest-service.berest.util :as bu]
            [datomic.api :as d]
            #_[miner.ftp :as ftp]
            [clojure.tools.logging :as log]
            [clojure.pprint :as pp]))

(defn- parse-german-double [text]
  (double (.. java.text.NumberFormat (getInstance java.util.Locale/GERMAN) (parse text))))

(defn parse-prognosis-data
  "parse a DWD prognosis data file and return datomic transaction data"
  [data]
  (let [data* (str/split-lines data)
        data** (->> data* (drop 7 ,,,) (take 9))
        stations (-> data** first (str/split ,,, #"\s+"))]
    (for [line (drop 3 data**)
          :let [line* (str/split line #"\s+")
                date (first line*)
                date* (->> date (ctf/parse (ctf/formatter "ddMMyyyy") ,,,) ctcoe/to-date)]
          [station [rr-s vp-t gs tm]] (map vector
                                           (rest stations)
                                           (partition 4 (rest line*)))]
      {:weather-station/id station
       :weather-station/data {:weather-data/prognosis-data? true
                              :weather-data/date date*
                              :weather-data/precipitation (parse-german-double rr-s)
                              :weather-data/evaporation (parse-german-double vp-t)
                              :weather-data/average-temperature (parse-german-double tm)
                              :weather-data/global-radiation (parse-german-double gs)}})))

(comment "instarepl debugging code"
  (def pdata
    #_(slurp "resources/private/climate/FY60DWLA-20130530_0815.txt")
    (slurp "resources/private/climate/FY60DWLA-20140203_0915.txt"))
  (def pdata* (parse-and-transform-prognosis-data pdata))
  (pp/pprint pdata*)
  )

(defn parse-measured-data
  "parse ad DWD measured data file and return ready datomic transaction data"
  [data]
  (let [data* (str/split-lines data)
        data** (->> data* (drop 6 ,,,) (take 3))
        stations (-> data** first (str/split ,,, #"\s+"))]
    (for [line (drop 2 data**)
          :let [line* (str/split line #"\s+")
                date (first line*)
                date* (->> date (ctf/parse (ctf/formatter "dd.MM.yyyy") ,,,) ctcoe/to-date)]
          [station [rr-s vp-t gs tm]] (map vector
                                           (rest stations)
                                           (partition 4 (rest line*)))]
      {:weather-station/id station
       :weather-station/data {:weather-data/date date*
                              :weather-data/precipitation (parse-german-double rr-s)
                              :weather-data/evaporation (parse-german-double vp-t)
                              :weather-data/average-temperature (parse-german-double tm)
                              :weather-data/global-radiation (parse-german-double gs)}})))

(comment "instarepl debugging code"
  (def mdata
    #_(slurp "resources/private/climate/FY60DWLB-20130526_0815.txt")
    (slurp "resources/private/climate/FY60DWLB-20140203_0915.txt"))
  (def mdata* (parse-and-transform-measured-data mdata))
  (pp/pprint mdata*)
  (as-transaction-fns mdata*)
  )


(defn add-data
  "A transaction function creating data and just allowing unique data per station and day"
  [db data]
  (let [station-id (:weather-station/id data)
        date (-> data :weather-station/data :weather-data/date)
        q (datomic.api/q '[:find ?se ?e
                           :in $ ?station-id ?date
                           :where
                           [?se :weather-station/id ?station-id]
                           [?se :weather-station/data ?e]
                           [?e :weather-data/date ?date]]
                         db station-id date)
        [station-entity-id data-entity-id] (first q)
        data* (if data-entity-id
                (assoc-in data [:weather-station/data :db/id] data-entity-id)
                data)]
    ;always create a temporary db/id, will be upsert if station exists already
    [(assoc data* :db/id (datomic.api/tempid :db.part/user))]))


(comment "insert transaction function into db, without full schema reload"
  (d/transact (bd/datomic-connection bd/*db-id*)
            [(read-string "{:db/id #db/id[:db.part/user]
  :db/ident :weather-station/add-data
  :db/doc \"A transaction function creating data and just allowing unique data per station and day\"
  :db/fn #db/fn {:lang \"clojure\"
                 :params [db data]
                 :code \"(let [station-id (:weather-station/id data)
        date (-> data :weather-station/data :weather-data/date)
        q (datomic.api/q '[:find ?se ?e
                           :in $ ?station-id ?date
                           :where
                           [?se :weather-station/id ?station-id]
                           [?se :weather-station/data ?e]
                           [?e :weather-data/date ?date]]
                         db station-id date)
        [station-entity-id data-entity-id] (first q)
        data* (if data-entity-id
                (assoc-in data [:weather-station/data :db/id] data-entity-id)
                data)]
    ;always create a temporary db/id, will be upsert if station exists already
    [(assoc data* :db/id (datomic.api/tempid :db.part/user))])\"}}")])
  )

(comment "instarepl test"
  (add-data (bd/current-db) {:weather-station/id "N652",
                             :weather-station/data
                             {:weather-data/prognosis-data? true,
                              :weather-data/date #inst "2014-02-08T00:00:00.000-00:00",
                              :weather-data/precipitation 4.5,
                              :weather-data/evaporation 0.7,
                              :weather-data/average-temperature 4.2,
                              :weather-data/global-radiation 444.0}})

  (datomic.api/q '[:find ?se ?station-id
                 :in $
                 :where
                 [?se :weather-station/id ?station-id]
                 #_[?se :weather-station/data ?e]
                 #_[?e :weather-data/date ?date]]
               (bd/current-db) "10162" #inst "2014-02-04T00:00:00.000-00:00")
  )


(defn make-prognosis-filename [date]
  (str "FY60DWLA-" (ctf/unparse (ctf/formatter "yyyyMMdd") date) "_0915.txt"))

(defn make-measured-filename [date]
  (str "FY60DWLB-" (ctf/unparse (ctf/formatter "yyyyMMdd") date) "_0915.txt"))

(comment "instarepl debug code"
  (make-prognosis-filename (ctc/date-time 2013 6 3))

  ;real ftp seams to be not necessary for just getting data (at least for anonymous access and co)
  (def t (ftp/with-ftp [client "ftp://anonymous:pwd@tran.zalf.de/pub/net/wetter"]
                       (ftp/client-get-stream client (make-prognosis-filename (ctc/date-time 2013 6 3)))))

  (clojure.java.io/reader t)
  )

(defn import-dwd-data-into-datomic
  "import the requested kind [:prognosis | :measured] dwd data into datomic"
  [kind & [date]]
  (try
    (let [date* (or date (ctc/now))
          url "ftp://tran.zalf.de/pub/net/wetter/"
          url* (str url (case kind
                          :prognosis (make-prognosis-filename date*)
                          :measured (make-measured-filename date*)))
          data (try
                 (slurp url*)
                 (catch Exception e
                   (log/info (str "Couldn't read " (name kind) " file from ftp server! URL was " url*))
                   (throw e)))
          transaction-data (case kind
                             :prognosis (parse-prognosis-data data)
                             :measured (parse-measured-data data))

          ;insert transaction data via :weather-station/add-data transaction function, to create unique data per station and day
          transaction-data->add-data (map #(vector :weather-station/add-data %) transaction-data)
          ]
      (try
        (d/transact (bd/datomic-connection bd/*db-id*) transaction-data->add-data)
        (catch Exception e
          (log/info "Couldn't write dwd data to datomic! data: [\n" transaction-fns-data "\n]")
          (throw e)))
      true)
    (catch Exception _ false)))

(comment
  (import-dwd-data-into-datomic :prognosis (ctc/date-time 2014 2 4))
  (import-dwd-data-into-datomic :measured (ctc/date-time 2014 2 4))
  )

