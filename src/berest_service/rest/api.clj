(ns berest-service.rest.api
  (:require [clojure.string :as cs]
            [datomic.api :as d]
            [ring.util.response :as rur]
            [hiccup.element :as he]
            [hiccup.def :as hd]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [clojure.pprint :as pp]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [berest.core :as bc]
            [berest.plot :as plot]
            [berest.datomic :as db]
            [berest.util :as bu]
            [berest.climate.climate :as climate]
            [berest-service.rest.common :as common]
            [berest-service.rest.queries :as queries]
            [berest-service.rest.template :as temp]
            [let-else :as le :refer [let?]]
            [simple-time.core :as time]))

;; page translations

(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:wstations {:lang/de "Wetterstationen"
                       :lang/en "weather stations"}
           :show {:lang/de "Hier werden alle in der Datenbank
                  gespeicherten Wetterstationen angezeigt."
                  :lang/en "Here will be displayed all weather stations
                  stored in the database."}
           :create {:lang/de "Neue Wetterstation erstellen:"
                    :lang/en "Create new weather station:"}
           :create-button {:lang/de "Erstellen"
                           :lang/en "Create"}

           }
          [element (or lang common/*lang*)] "UNKNOWN element"))

;; page layouts

(defn api-layout [db url]
  [:div.container
   (temp/standard-get-post-h3 url)

   #_[:div
    [:h4 (str (vocab :farms) " (GET " url ")")]
    [:p (vocab :show)]
    [:hr]
    [:ul#farms
     (for [fe #_(get-farm-entities)]
       [:li [:a {:href (str url "/" (:farm/id fe))} (or (:farm/name fe) (:farm/id fe))]])
     ]
    [:hr]
    [:h4 "application/edn"]
    [:code (pr-str (map :farm/id #_(get-farm-entities)))]
    [:hr]
    ]

   #_[:div
    [:h4 (str (vocab :create)" (POST " url ")")]
    [:form.form-horizontal {:role :form
                            :method :post
                            :action url}
     #_(create-farms-layout)]
    ]])

(defn get-api
  [request]
  "get-api"
  #_(let [db (db/current-db)]
    (common/standard-get (partial api-layout db)
                         request)))


(defn get-auth-api
  [request]
  "get-auth-api"
  #_(let [db (db/current-db)]
    (common/standard-get (partial api-layout db)
                         request)))


;; api functions

(defn calculate-plot-from-db
  [& {:keys [db farm-id plot-id weather-station-id
             until-julian-day year
             irrigation-donations dc-assertions]}]
  (let? [plot (bc/db-read-plot db plot-id year)
         :else [:div#error "Fehler: Konnte Schlag mit Nummer: " plot-id " nicht laden!"]

         ;plot could be updated with given dc-assertions
         ;plot* (update-in plot [])

         weather (climate/weather-data db weather-station-id year)
         sorted-weather-map (into (sorted-map) (map #(vector (bu/date-to-doy (:weather-data/date %)) %)
                                                    weather))

         inputs (bc/create-input-seq| :plot plot
                                      :sorted-weather-map weather
                                      :irrigation-donations irrigation-donations
                                      :until-abs-day (+ until-julian-day 7)
                                      :irrigation-mode :sprinkle-losses)
         inputs-7 (drop-last 7 inputs)

         ;xxx (map (|-> (--< :abs-day :irrigation-amount) str) inputs-7)
         ;_ (println xxx)

         prognosis-inputs (take-last 7 inputs)
         days (range (-> inputs first :abs-day) (+ until-julian-day 7 1))

         sms-7* (bc/calc-soil-moistures* inputs-7 (:plot.annual/initial-soil-moistures plot))
         {soil-moistures-7 :soil-moistures
          :as sms-7} (last sms-7*)
         #_(bc/calc-soil-moistures inputs-7 (:plot.annual/initial-soil-moistures plot))

         prognosis* (bc/calc-soil-moisture-prognosis* 7 prognosis-inputs soil-moistures-7)
         prognosis (last prognosis*)
         #_(bc/calc-soil-moisture-prognosis 7 prognosis-inputs soil-moistures-7)
         ]

        {:soil-moistures-7 sms-7*
         :prognosis prognosis*}))

(defn- split-plot-id-format [plot-id-format]
  (-> plot-id-format
      (cs/split ,,, #"\.")
      (#(split-at (-> % count dec (max 1 ,,,)) %) ,,,)
      (#(map (partial cs/join ".") %) ,,,)))


(defn calculate
  [{{:keys [farm-data plot-data
            weather-data
            irrigation-data
            dc-assertion-data]} :params
    {:keys [user-id]} :identity}]
  false
  #_(let [year* (Integer/parseInt year)
        until-julian-day (bu/date-to-doy (Integer/parseInt until-day)
                                         (Integer/parseInt until-month)
                                         year*)
        irrigation-donations (for [[day month amount] (edn/read-string irrigation-data)]
                               {:irrigation/abs-day (bu/date-to-doy day month year*)
                                :irrigation/amount amount})]
    (calculate-plot-from-db :user-id user-id :farm-id farm-id :plot-id plot-id
                            :until-julian-day until-julian-day :year year*
                            :weather-station-id weather-station-id
                            :irrigation-donations irrigation-donations)))

(defn auth-calculate
  "use calculation service, but mainly using a users data taken from database"
  [request]
  (let [user-id (-> request :session :identity :user/id)
        {:keys [farm-id
                plot-id
                until-date
                irrigation-data]} (-> request :path-params)
        db (db/current-db user-id)
        until-date* (time/parse until-date)
        year (time/datetime->year until-date*)
        until-julian-day (time/datetime->day-of-year until-date*)
        irrigation-donations (for [[day month amount] (edn/read-string irrigation-data)]
                               {:irrigation/abs-day (time/datetime->day-of-year (time)) (bu/date-to-doy day month year*)
                                :irrigation/amount amount})]
    (calculate-plot-from-db :db db
                            :farm-id farm-id :plot-id plot-id
                            :until-julian-day until-julian-day :year year*
                            :weather-station-id weather-station-id
                            :irrigation-donations irrigation-donations)))


(defn simulate
  [{:keys [query-params path-params] :as request}]
  (let [{format :format :as data} query-params
        {farm-id :farm-id
         plot-id-format :plot-id-format} path-params
        [plot-id format*] (split-plot-id-format plot-id-format)
        plot-id "zalf"]
    false
    #_(-> (simulate-plot :user-id "guest" :farm-id farm-id :plot-id plot-id :data data)
        rur/response
        (rur/content-type ,,, "text/csv"))))

(defn auth-simulate
  [{:keys [query-params path-params] :as request}]
  (let [{format :format :as data} query-params
        {farm-id :farm-id
         plot-id-format :plot-id-format} path-params
        [plot-id format*] (split-plot-id-format plot-id-format)
        plot-id "zalf"]
    false
    #_(-> (simulate-plot :user-id "guest" :farm-id farm-id :plot-id plot-id :data data)
        rur/response
        (rur/content-type ,,, "text/csv"))))


#_(defn simulate-plot
  [& {:keys [user-id farm-id plot-id]
      {:keys [until-day until-month
              weather-year
              #_dc-state-data]} :data
      :as all}]
  (let? [db (bd/current-db user-id)
         :else [:div#error "Fehler: Konnte keine Verbindung zur Datenbank herstellen!"]

         weather-year* (Integer/parseInt weather-year)
         weathers (get bc/weather-map weather-year*)

         plot (bc/db-read-plot db plot-id weather-year*)
         :else [:div#error "Fehler: Konnte Schlag mit Nummer: " plot-id " nicht laden!"]

         until-julian-day (bu/date-to-doy (Integer/parseInt until-day)
                                          (Integer/parseInt until-month)
                                          weather-year*)

         inputs (bc/create-input-seq| :plot plot
                                      :sorted-weather-map weathers
                                      :until-abs-day until-julian-day #_(+ until-julian-day 7)
                                      :irrigation-mode :sprinkle-losses)

         ;xxx (map (|-> (--< :abs-day :irrigation-amount) str) inputs)
         ;_ (println xxx)

         days (range (-> inputs first :abs-day) (+ until-julian-day 1))

         sms* (bc/calculate-soil-moistures-by-auto-donations* inputs (:plot.annual/initial-soil-moistures plot)
                                                              (:plot/slope plot) (:plot.annual/technology plot) 5)
         {soil-moistures :soil-moistures
          :as sms} (last sms*)
         #_(bc/calc-soil-moistures inputs-7 (:plot.annual/initial-soil-moistures plot))

         ;_ (map pp/pprint sms*)
         ]

        ;use rest on sms-7* etc. to skip the initial value prepended by reductions
        ;which doesn't fit to the input list
        (csv/write-csv (bc/create-csv-output inputs (rest sms*)) :delimiter ";")))



