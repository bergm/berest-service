(ns de.zalf.berest.web.rest.api
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
            [de.zalf.berest.core.core :as bc]
            [de.zalf.berest.core.api :as api]
            [de.zalf.berest.core.plot :as plot]
            [de.zalf.berest.core.datomic :as db]
            [de.zalf.berest.core.data :as data]
            [de.zalf.berest.core.util :as bu]
            [de.zalf.berest.core.helper :as bh :refer [rcomp]]
            [de.zalf.berest.core.climate.climate :as climate]
            [de.zalf.berest.web.rest.common :as common]
            [de.zalf.berest.web.rest.queries :as queries]
            [de.zalf.berest.web.rest.template :as temp]
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
   (temp/standard-header url)

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
  [db farm-id plot-id until-julian-day year irrigation-donations dc-assertions]
  (if-let [plot (bc/deep-db->plot db plot-id year)]
    (let [;plot could be updated with given dc-assertions
          ;plot* (update-in plot [])

           sorted-weather-map (climate/final-sorted-weather-data-map-for-plot db year plot-id)

           inputs (bc/create-input-seq plot sorted-weather-map (+ until-julian-day 7)
                                       irrigation-donations :technology.type/sprinkler)
           inputs-7 (drop-last 7 inputs)

          ;xxx (map (rcomp (juxt :abs-day :irrigation-amount) str) inputs-7)
          ;_ (println xxx)

           prognosis-inputs (take-last 7 inputs)
           days (range (-> inputs first :abs-day) (+ until-julian-day 7 1))

           sms-7* (bc/calc-soil-moistures* inputs-7 (:plot.annual/initial-soil-moistures plot))
           {soil-moistures-7 :soil-moistures
            :as sms-7} (last sms-7*)
          #_(bc/calc-soil-moistures inputs-7 (:plot.annual/initial-soil-moistures plot))

           prognosis* (bc/calc-soil-moisture-prognosis* 7 prognosis-inputs soil-moistures-7)
           prognosis (last prognosis*)
          #_(bc/calc-soil-moisture-prognosis 7 prognosis-inputs soil-moistures-7)]
      {:inputs inputs
       :soil-moistures-7 (rest sms-7*)
       :prognosis prognosis*})
    (str "Fehler: Konnte Schlag " plot-id " nicht laden!")))

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

(defn create-liberator-csv-output
  [csv-out]
  (let [header (first csv-out)
        body (rest csv-out)]
    (map #(apply array-map (interleave header %)) body)))

(defn auth-calculate
  "use calculation service, but mainly using a users data taken from database"
  [media-type request]
  (let [user-id (-> request :session :identity :user/id)
        {:keys [farm-id
                plot-id
                until-date
                irrigation-data]} (-> request :params)
        db (db/current-db)
        until-date* (time/parse until-date :year-month-day)
        year (time/datetime->year until-date*)
        until-julian-day (time/datetime->day-of-year until-date*)
        donations (for [[day month amount] (edn/read-string irrigation-data)]
                    {:donation/abs-start-day (time/datetime->day-of-year (time/datetime year month day))
                     :donation/abs-end-day (time/datetime->day-of-year (time/datetime year month day))
                     :donation/amount amount})
        {:keys [inputs soil-moistures-7 prognosis] :as result}
        (calculate-plot-from-db db farm-id plot-id until-julian-day year
                                donations [])
        soil-moistures (concat soil-moistures-7 prognosis)
        csv-sms (->> soil-moistures
                     (api/create-csv-output inputs ,,,)
                     create-liberator-csv-output)]
    (case media-type
      "text/html" csv-sms
      "application/edn" result #_soil-moistures
      "application/json" result #_soil-moistures
      "text/csv" csv-sms
      "text/tab-separated-values" csv-sms)))


(defn simulate-plot-from-db
  [db farm-id plot-id until-julian-day year donations dc-assertions]
  (if-let [plot (bc/deep-db->plot db plot-id year)]
    (let [;plot could be updated with given dc-assertions
          ;plot* (update-in plot [])

           sorted-weather-map (climate/final-sorted-weather-data-map-for-plot db year plot-id)

           inputs (bc/create-input-seq plot
                                       sorted-weather-map
                                       (+ until-julian-day 7)
                                       donations
                                       (-> plot :plot.annual/technology :technology/type))

          ;xxx (map (rcomp (juxt :abs-day :irrigation-amount) str) inputs-7)
          ;_ (println xxx)

           days (range (-> inputs first :abs-day) (inc until-julian-day))

           sms* (bc/calculate-soil-moistures-by-auto-donations*
                  inputs (:plot.annual/initial-soil-moistures plot)
                  (-> plot :plot/slope :slope/key)
                  (:plot.annual/technology plot)
                  5)

           {soil-moistures :soil-moistures
            :as sms} (last sms*)
           #_(bc/calculate-soil-moistures-by-auto-donations* inputs (:plot.annual/initial-soil-moistures plot)
                                                           (-> plot :plot/slope :slope/key) (:plot.annual/technology plot) 5)
           ]
      {:inputs inputs
       :soil-moistures sms*})
    (str "Fehler: Konnte Schlag " plot-id " nicht laden!")))


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
  "use simulation service, but mainly using a users data taken from database"
  [media-type request]
  (let [user-id (-> request :session :identity :user/id)
        {:keys [farm-id
                plot-id
                until-date
                irrigation-data]} (-> request :params)
        db (db/current-db)
        until-date* (time/parse until-date :year-month-day)
        year (time/datetime->year until-date*)
        until-julian-day (time/datetime->day-of-year until-date*)
        irrigation-donations (for [[day month amount] (edn/read-string irrigation-data)]
                               {:donation/abs-start-day (time/datetime->day-of-year (time/datetime year month day))
                                :donation/abs-end-day (time/datetime->day-of-year (time/datetime year month day))
                                :donation/amount amount})
        {:keys [inputs soil-moistures] :as result}
        (simulate-plot-from-db db farm-id plot-id until-julian-day year
                               irrigation-donations [])
        csv-sms (->> soil-moistures
                     (api/create-csv-output inputs ,,,)
                     create-liberator-csv-output)]
    (case media-type
          "text/html" csv-sms
          "application/edn" result #_soil-moistures
          "application/json" result #_soil-moistures
          "text/csv" csv-sms
          "text/tab-separated-values" csv-sms)))

