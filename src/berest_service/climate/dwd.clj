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

(comment "insta parse version, which cause problems all the time"
  (def prognosis-data-parser
    (insta/parser
     "
     prognosis-data =
     <empty-line*>
     <drop-line drop-line drop-line drop-line drop-line drop-line drop-line>
     header-line
     <drop-line drop-line>
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
     the next line is the old format with just evaporation
     30052013      3,5      3,0      3,3      3,2      3,0      2,8      2,3      2,0      2,4      2,7      2,9      2,8      1,1      2,0      0,9      2,0      1,3      3,9      3,2      2,6      3,3      2,6      2,3      2,2      1,8      1,7
     data-line = <ows> date (<ws-without-newline> double)+ <ows-without-newline newline>

     the following line is the current file format, with four values
     03022014   0,0   0,4  270,0   0,0    0,0   0,4  240,0   0,0    0,0   0,4  270,0   0,4    0,0   0,4  250,0   0,6    0,0   0,5  340,0  -0,6    0,0   0,5  330,0  -0,3    0,1   0,6  370,0   1,6    0,3   0,5  330,0   0,9    0,0   0,6  390,0   0,5    0,0   0,6  430,0   0,8    0,2   0,7  460,0   1,0    0,0   0,7  440,0   1,1    0,7   0,8  550,0   0,7    0,1   0,4  200,0   0,2    0,0   0,4  220,0   0,3    0,0   0,5  320,0   0,2    0,1   0,5  300,0   0,5    0,4   0,5  270,0   1,1    0,1   0,5  340,0   0,7    0,1   0,6  400,0   1,8    1,0   0,7  460,0   1,1    0,0   0,5  330,0  -0,4    0,0   0,6  370,0   0,2
     *)
     data-line = <ows> date data-tuple+ <ows-without-newline newline>
     data-tuple =
     <ws-without-newline> double (* precipitation [mm] *)
     <ws-without-newline> double (* turc wendling evaporation [mm] *)
     <ws-without-newline> double (* global radiation [J/qcm] *)
     <ws-without-newline> double (* average air temperature 2m [°C] *)
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
                 :data-tuple (fn [rr-s vp-t gs tm]
                               {:precip rr-s
                                :evap vp-t
                                :globrad gs
                                :tavg tm})

                 :header-line (fn [& stations] {:stations stations})

                 :data-line (fn [& [date & data]]
                              {:date date
                               :data data})

                 :prognosis-data (fn [{stations :stations} & data]
                                   (flatten
                                    (map (fn [{:keys [date data]}]
                                           (map (fn [station & prognosis-data]
                                                  (for [{:keys [precip evap globrad tavg]} prognosis-data]
                                                    {:weather-station/id station
                                                     :weather-station/data {:weather-data/prognosis-data? true
                                                                            :weather-data/date date
                                                                            :weather-data/precipitation precip
                                                                            :weather-data/evaporation evap
                                                                            :weather-data/average-temperature tavg
                                                                            :weather-data/global-radiation globrad}}))
                                                stations data))
                                         data)))}]
      (insta/transform trans pdata)))

  (defn parse-and-transform-prognosis-data [text-data]
    (-> text-data
        prognosis-data-parser
        transform-prognosis-data))

  (comment "insta repl code"
    (def pdata
      #_(slurp "resources/private/climate/FY60DWLA-20130530_0815.txt")
      (slurp "resources/private/climate/FY60DWLA-20140203_0915.txt"))
    (def p (insta/parses prognosis-data-parser pdata :total true))
    (count p)
    (pp/pprint (nth p 0))
    (pp/pprint (transform-prognosis-data (first p)))
    (parse-and-transform-prognosis-data (slurp "resources/private/climate/FY60DWLA-20130530_0815.txt"))
    )
)


(defn parse-prognosis-data
  "parse a DWD prognosis data file and
  return ({:station climate-station-id-1 :date date-1 :evap evaporation-1} {:station climate-station-id-1 :date date-2 :evap evaporation-2} ...)"
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

(comment "insta parse version, which has been abandoned because is basically too complicated"
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
     next line is just with precipitation and evaporation
     25.05.2013  14,1   0,8   12,1   1,0   12,8   1,2   16,7   1,2   28,4   0,8   14,4   1,8   19,1   1,9   19,9   1,6   13,9   1,2   23,1   1,3   24,7   1,0   17,6     1,1    6,2   2,2   17,5   1,8    5,4   2,5    6,5   1,8    0,0   1,9   10,6     1,2    9,2   1,2   18,0     1,3   25,9     1,2   27,5   1,2   16,5   2,1    9,7   1,2    2,7   1,3   11,3   2,1

     the following line is the current measured data file with four values
     02.02.2014   0,0   0,5  284,0   2,8    0,0   0,5  257,0   3,1    1,2   0,4  201,0   1,6    0,8   0,4  201,0   1,7    0,3   0,4  247,0   2,2    0,5   0,5  264,0   2,1    0,3   0,4  209,0   2,4    6,8   0,3  179,0   1,5    1,3   0,4  240,0   3,1    1,8   0,4  214,0   2,7    0,4   0,3  163,0   1,7    3,7   0,3  142,0   1,7    0,1   0,3  175,0   1,0    0,6   0,4  189,0   1,5    1,4   0,4  198,0   2,0    0,1   0,4  204,0   2,7    0,7   0,4  202,0   2,0    7,2   0,4  203,0   1,9    0,8   0,4  209,0   1,2    7,8   0,4  208,0   1,7    5,3   0,4  214,0   1,6    0,3   0,4  208,0   2,5    1,0   0,4  211,0   1,6
     *)
     data-line = <ows> date data-tuple+ <ows-without-newline newline>
     data-tuple =
     <ws-without-newline> double (* precipitation [mm] *)
     <ws-without-newline> double (* turc wendling evaporation [mm] *)
     <ws-without-newline> double (* global radiation [J/qcm] *)
     <ws-without-newline> double (* average air temperature 2m [°C] *)
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
                 :data-tuple (fn [rr-s vp-t gs tm]
                               {:precip rr-s
                                :evap vp-t
                                :globrad gs
                                :tavg tm})

                 :header-line (fn [& stations] {:stations stations})

                 :data-line (fn [& [date & data]]
                              {:date date
                               :data data})

                 :measured-data (fn [{stations :stations} & data]
                                  (flatten
                                   (map (fn [{:keys [date data]}]
                                          (map (fn [station & measured-data]
                                                 (for [{:keys [precip evap globrad tavg]} measured-data]
                                                   {:weather-station/id station
                                                    :weather-station/data {:weather-data/date date
                                                                           :weather-data/precipitation precip
                                                                           :weather-data/evaporation evap
                                                                           :weather-data/average-temperature tavg
                                                                           :weather-data/global-radiation globrad}}))
                                               stations data))
                                        data)))}]
      (insta/transform trans mdata)))

  (defn parse-and-transform-measured-data [text-data]
    (-> text-data
        measured-data-parser
        transform-measured-data))

  (comment "instarepl code"
    (def mdata
      #_(slurp "resources/private/climate/FY60DWLB-20130526_0815.txt")
      (slurp "resources/private/climate/FY60DWLB-20140203_0915.txt"))
    (def p (insta/parses measured-data-parser mdata :total true))
    (count p)
    (pp/pprint (nth p 0))
    (pp/pprint (transform-prognosis-data (first p)))
    (parse-and-transform-measured-data (slurp "resources/private/climate/FY60DWLB-20130526_0815.txt"))
    )
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


;; insert transaction function into db
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


#_(datomic.api/q '[:find ?se ?station-id
                 :in $
                 :where
                 [?se :weather-station/id ?station-id]
                 #_[?se :weather-station/data ?e]
                 #_[?e :weather-data/date ?date]]
               (bd/current-db) "10162" #inst "2014-02-04T00:00:00.000-00:00")


(comment "instarepl test"
  (add-data (bd/current-db) {:weather-station/id "N652",
                             :weather-station/data
                             {:weather-data/prognosis-data? true,
                              :weather-data/date #inst "2014-02-08T00:00:00.000-00:00",
                              :weather-data/precipitation 4.5,
                              :weather-data/evaporation 0.7,
                              :weather-data/average-temperature 4.2,
                              :weather-data/global-radiation 444.0}})
  )

(defn as-transaction-fns [data]
  (map #(vector :weather-station/add-data %) data))

(comment "instarepl debugging code"
  (def mdata
    #_(slurp "resources/private/climate/FY60DWLB-20130526_0815.txt")
    (slurp "resources/private/climate/FY60DWLB-20140203_0915.txt"))
  (def mdata* (parse-and-transform-measured-data mdata))
  (pp/pprint mdata*)
  (as-transaction-fns mdata*)
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
                   (println #_log/info (str "Couldn't read " (name kind) " file from ftp server! URL was " url*))
                   (throw e)))
          transaction-data (case kind
                             :prognosis (parse-prognosis-data data)
                             :measured (parse-measured-data data))
          _ (pp/pprint transaction-data)
          transaction-fns-data (as-transaction-fns transaction-data)
          _ (pp/pprint transaction-fns-data)]
      (try
        (d/transact (bd/datomic-connection bd/*db-id*) transaction-fns-data)
        (catch Exception e
          (println #_log/info "Couldn't write dwd data to datomic! data: [\n" transaction-fns-data "\n]")
          (throw e)))
      true)
    (catch Exception _ false)))

(comment
  (import-dwd-data-into-datomic :prognosis (ctc/date-time 2014 2 4))
  (import-dwd-data-into-datomic :measured (ctc/date-time 2014 2 4))
  )

#_(import-dwd-data-into-datomic :measured (ctc/date-time 2014 2 4))
#_(import-dwd-data-into-datomic :prognosis (ctc/date-time 2014 2 4))





