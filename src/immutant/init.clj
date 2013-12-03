(ns immutant.init
  (:require [immutant.web :as web]
            [immutant.jobs :as jobs]
            [io.pedestal.service.http :as http]
            [berest-service.service :as service]
            [berest-service.climate.dwd :as dwd]))

;for ring handlers
#_(:use [ring.middleware.resource :only [wrap-resource]]
        [ring.util.response :only [redirect]])
#_(defn handler [request]
  (redirect "/index.html"))
#_(immutant.web/start (wrap-resource handler "public"))

;for pedestal service apps
(web/start-servlet "/" (::http/servlet (http/create-servlet service/service)))



(jobs/schedule :test-fetch-dwd-climate-data
               (partial dwd/import-dwd-data-into-datomic :measured)
               :in [1 :minute]
               :every [2 :minutes]
               :repeat 3
               :singleton true) ;just one server has to fetch and store the data

#_(jobs/schedule :fetch-dwd-prognosis-data
               (partial dwd/import-dwd-data-into-datomic :prognosis)
               :at "10:30"
               :every [3 :days]
               :singleton true)

#_(jobs/schedule :fetch-dwd-measured-data
               (partial dwd/import-dwd-data-into-datomic :measured)
               :at "10:30"
               :every :day
               :singleton true)



