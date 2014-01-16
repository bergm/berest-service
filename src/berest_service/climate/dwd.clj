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
            [instaparse.core :as insta]
            [clojure.pprint :as pp]
            [clojure.set]))

(comment
  (def on-server (file-seq (cjio/file "r:/carbiocial/climate-data/gs_1981-2013")))
  (def os-set (into #{} (map #(.getName %) on-server)))
  (def local (file-seq (cjio/file "I:/gs_1981-2013")))
  (def l-set (into #{} (map #(.getName %) local)))

  (def diff (clojure.set/difference os-set l-set))
  )

(defn- parse-german-double [text]
  (double (.. java.text.NumberFormat (getInstance java.util.Locale/GERMAN) (parse text))))



(comment "prognosis data example"
  "
  Datenlieferung IRRIGAMA
  Vorhersage der Verdunstung nach Turc/Wendling
  Erstellung am Donnerstag, den 30.05.2013, 10:00 Uhr GZ

  ** Bei Rückfragen (034297/989-275) bitte Produktbezeichnung angeben:
  ** FYDL60 DWLA 301000

  Kennung/    10162    10264    10267    10270    10291    10359    10361    10368    10376    10379    10382    10393    10469    10474    10480    10490    10499     B488     F143     F419     F431     F475     F707     F742     F951     N652
  Datum
  30052013      3,5      3,0      3,3      3,2      3,0      2,8      2,3      2,0      2,4      2,7      2,9      2,8      1,1      2,0      0,9      2,0      1,3      3,9      3,2      2,6      3,3      2,6      2,3      2,2      1,8      1,7
  31052013      2,7      2,2      3,2      3,3      3,7      2,3      3,2      3,4      3,2      3,8      3,7      2,9      3,1      3,4      2,7      2,8      3,4      3,2      2,7      3,0      3,2      3,8      3,8      3,0      2,6      3,0
  01062013      2,8      3,0      3,0      1,6      0,9      1,3      1,4      1,3      0,7      0,7      0,7      1,0      1,1      0,6      0,7      0,8      0,7      1,0      3,4      1,8      1,2      1,0      0,6      1,0      0,7      1,9
  02062013      3,3      2,9      3,1      2,8      3,0      2,7      2,5      2,3      2,4      2,6      2,6      3,0      1,9      2,1      2,2      2,3      2,8      3,1      2,8      2,1      2,5      2,7      2,5      2,0      2,4      2,5
  03062013      3,7      3,6      3,7      3,5      3,1      3,5      3,3      3,0      3,0      3,0      2,9      3,0      3,0      3,2      2,6      2,7      2,2      3,4      3,4      2,6      3,0      2,7      3,0      2,7      2,8      3,0


  Erläuterungen:
  Kenn = Stationskennung DWD
  Dat = Datum der Vorhersage (TagMonatJahr)
  potentielle Verdunstung, Turc/Wendling (mm)

  DWD Abt. Agrarmeteorologie
  "
  )

(def prognosis-data-parser
  (insta/parser
   "
   prognosis-data =
   <empty-line*>
   <drop-line drop-line drop-line drop-line drop-line drop-line drop-line>
   header-line
   <drop-line>
   data-line+
   <empty-line>
   <drop-line* ows-without-newline EOF>

   drop-line = rest-of-line

   (*
   Kennung/    10162    10264    10267    10270    10291    10359    10361    10368    10376    10379    10382    10393    10469    10474    10480    10490    10499     B488     F143     F419     F431     F475     F707     F742     F951     N652
   *)
   header-line = <ows 'Kennung/'> (<ws-without-newline> station-id)+ <ows-without-newline newline>
   station-id = #'[A-Z]?[0-9]+'

   (*
   30052013      3,5      3,0      3,3      3,2      3,0      2,8      2,3      2,0      2,4      2,7      2,9      2,8      1,1      2,0      0,9      2,0      1,3      3,9      3,2      2,6      3,3      2,6      2,3      2,2      1,8      1,7
   *)
   data-line = <ows> date (<ws-without-newline> double)+ <ows-without-newline newline>
   date = #'[0-9]+'

   rest-of-line = #'[^\\n\\r]*' (newline | EOF)
   empty-line = newline | ws-without-newline newline
   newline = '\\r\\n' | '\\n'
   ows-without-newline = #'[^\\S\\n\\r]*'
   ws-without-newline = #'[^\\S\\n\\r]+'
   ows = #'\\s*'
   ws = #'\\s+'
   double = #'[0-9]+(?:,[0-9]*)?'
   SOF = #'\\A'
   EOF = #'\\Z'
   "))

(defn transform-prognosis-data [pdata]
  (let [trans {:double parse-german-double

               :station-id identity
               :date #(->> % (ctf/parse (ctf/formatter "ddMMyyyy") ,,,) ctcoe/to-date)

               :header-line (fn [& stations] {:stations stations})

               :data-line (fn [& [date & data]]
                            {:date date
                             :data data})

               :prognosis-data (fn [{stations :stations} & data]
                                 (flatten
                                  (map (fn [{:keys [date data]}]
                                         (map (fn [station & prognosis-data]
                                                (for [prognosis prognosis-data]
                                                  {:weather-station/id station
                                                   :weather-station/data {:weather-data/date date
                                                                          :weather-data/evaporation prognosis}}))
                                              stations data))
                                       data)))}]
    (insta/transform trans pdata)))

(defn parse-and-transform-prognosis-data [text-data]
  (-> text-data
      prognosis-data-parser
      transform-prognosis-data))

(comment "insta repl code"
  (def pdata (slurp "resources/private/climate/FY60DWLA-20130530_0815.txt"))
  (def p (insta/parses prognosis-data-parser pdata :total true))
  (count p)
  (pp/pprint (nth p 0))
  (pp/pprint (transform-prognosis-data (first p)))
  (parse-and-transform-prognosis-data (slurp "resources/private/climate/FY60DWLA-20130530_0815.txt"))
)



(comment "old version parsing prognosis data directly"
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
    (def pdata (slurp "resources/private/climate/FY60DWLA-20130530_0815.txt"))
    (def pdata* (parse-and-transform-prognosis-data pdata))
    )
  )


(comment "measured data example"
  "
  Datenlieferung IRRIGAMA
  Erstellung am Sonntag, den 26.05.2013, 10:00 Uhr GZ

  ** Bei Rückfragen (034297/989-275) bitte Produktbezeichnung angeben:
  ** FYDL60 DWLB 26.05.20131000

  Datum            10162        10264        10267        10270        10291        10359        10361        10368        10376        10379        10382        10393        10469        10474        10480        10490        10499         B488         F143         F419         F431         F475         F707         F742         F951         N652
  RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T   RR-S  VP-T
  25.05.2013  14,1   0,8   12,1   1,0   12,8   1,2   16,7   1,2   28,4   0,8   14,4   1,8   19,1   1,9   19,9   1,6   13,9   1,2   23,1   1,3   24,7   1,0   17,6     1,1    6,2   2,2   17,5   1,8    5,4   2,5    6,5   1,8    0,0   1,9   10,6     1,2    9,2   1,2   18,0     1,3   25,9     1,2   27,5   1,2   16,5   2,1    9,7   1,2    2,7   1,3   11,3   2,1


  Erläuterungen:
  St.-Kenn = Stationskennung (DWD)
  Datum = TagMonatJahr
  RR-S = Niederschlag (24-stündig) (mm)
  VP-T = potentielle Verdunstung, Turc/Wendling (mm)

  DWD Abt. Agrarmeteorologie
  "
  )

(def measured-data-parser
  (insta/parser
   "
   measured-data =
   <empty-line*>
   <drop-line drop-line drop-line drop-line drop-line drop-line>
   header-line
   <drop-line>
   data-line+
   <empty-line>
   <drop-line* ows-without-newline EOF>

   drop-line = rest-of-line

   (*
   Datum            10162        10264        10267        10270        10291        10359        10361        10368        10376        10379        10382        10393        10469        10474        10480        10490        10499         B488         F143         F419         F431         F475         F707         F742         F951         N652
   *)
   header-line = <ows 'Datum'> (<ws-without-newline> station-id)+ <ows-without-newline newline>
   station-id = #'[A-Z]?[0-9]+'

   (*
   25.05.2013  14,1   0,8   12,1   1,0   12,8   1,2   16,7   1,2   28,4   0,8   14,4   1,8   19,1   1,9   19,9   1,6   13,9   1,2   23,1   1,3   24,7   1,0   17,6     1,1    6,2   2,2   17,5   1,8    5,4   2,5    6,5   1,8    0,0   1,9   10,6     1,2    9,2   1,2   18,0     1,3   25,9     1,2   27,5   1,2   16,5   2,1    9,7   1,2    2,7   1,3   11,3   2,1
   *)
   data-line = <ows> date precip-evap-pair+ <ows-without-newline newline>
   precip-evap-pair = <ws-without-newline> double <ws-without-newline> double
   date = #'[0-9]?[0-9]\\.[0-9]?[0-9]\\.[0-9]{4}'

   rest-of-line = #'[^\\n\\r]*' (newline | EOF)
   empty-line = newline | ws-without-newline newline
   newline = '\\r\\n' | '\\n'
   ows-without-newline = #'[^\\S\\n\\r]*'
   ws-without-newline = #'[^\\S\\n\\r]+'
   ows = #'\\s*'
   ws = #'\\s+'
   double = #'[0-9]+(?:,[0-9]*)?'
   SOF = #'\\A'
   EOF = #'\\Z'
   "))

(defn transform-measured-data [mdata]
  (let [trans {:double parse-german-double

               :station-id identity
               :date #(->> % (ctf/parse (ctf/formatter "dd.MM.yyyy") ,,,) ctcoe/to-date)
               :precip-evap-pair (fn [precip evap]
                                   {:precip precip
                                    :evap evap})

               :header-line (fn [& stations] {:stations stations})

               :data-line (fn [& [date & data]]
                            {:date date
                             :data data})

               :measured-data (fn [{stations :stations} & data]
                                (flatten
                                 (map (fn [{:keys [date data]}]
                                        (map (fn [station & measured-data]
                                               (for [{:keys [precip evap]} measured-data]
                                                 {:weather-station/id station
                                                  :weather-station/data {:weather-data/date date
                                                                         :weather-data/precipitation precip
                                                                         :weather-data/evaporation evap}}))
                                             stations data))
                                      data)))}]
    (insta/transform trans mdata)))

(defn parse-and-transform-measured-data [text-data]
  (-> text-data
      measured-data-parser
      transform-measured-data))

(comment "instarepl code"
  (def mdata (slurp "resources/private/climate/FY60DWLB-20130526_0815.txt"))
  (def p (insta/parses measured-data-parser mdata :total true))
  (count p)
  (pp/pprint (nth p 0))
  (pp/pprint (transform-prognosis-data (first p)))
  (parse-and-transform-measured-data (slurp "resources/private/climate/FY60DWLB-20130526_0815.txt"))
  )



(comment "old direct parsing of measured data"
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
  )


(defn as-transaction-fns [data]
  (map (fn [d]
         [:weather-station/add-data
          (:weather-station/id d)
          (-> d :weather-station/data :weather-data/evaporation)
          (-> d :weather-station/data :weather-data/precipitation)])
       data))

(comment "instarepl debugging code"
  (def mdata (slurp "resources/private/climate/FY60DWLB-20130526_0815.txt"))
  (def mdata* (parse-and-transform-measured-data mdata))
  (as-transaction-fns mdata*)
  )

(defn make-prognosis-filename [date]
  (str "FY60DWLA-" (ctf/unparse (ctf/formatter "yyyyMMdd") date) "_0815.txt"))

(defn make-measured-filename [date]
  (str "FY60DWLB-" (ctf/unparse (ctf/formatter "yyyyMMdd") date) "_0815.txt"))


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
                             :prognosis (parse-and-transform-prognosis-data data)
                             :measured (parse-and-transform-measured-data data))]
      (try
        (d/transact (bd/datomic-connection bd/*db-id*) transaction-data)
        (catch Exception e
          (log/info "Couldn't write dwd data to datomic! data: [\n" transaction-data "\n]")
          (throw e)))
      true)
    (catch Exception _ false)))

(comment
  (import-dwd-data-into-datomic :prognosis (ctc/date-time 2013 6 6))
  )

#_(import-dwd-data-into-datomic :prognosis (ctc/date-time 2013 6 3))





