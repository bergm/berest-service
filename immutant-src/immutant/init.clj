(ns immutant.init
  (:require [immutant.web :as web]
            [immutant.jobs :as jobs]
            #_[io.pedestal.service.http :as http]
            #_[berest-service.service :as service]
            [berest.climate.dwd :as dwd]
            #_[ring.middleware.resource :refer [wrap-resource]]
            #_[ring.util.response :refer [redirect]]))

;for ring handlers
#_(defn handler [request]
  (redirect "/index.html"))
#_(immutant.web/start (wrap-resource handler "public"))

;for pedestal service apps
;(web/start-servlet "/" (::http/servlet (http/create-servlet service/service)))



#_(jobs/schedule :test-fetch-dwd-climate-data
               (partial dwd/import-dwd-data-into-datomic :measured)
               :in [1 :minute]
               :every [2 :minutes]
               :repeat 3
               :singleton true) ;just one server has to fetch and store the data

(jobs/schedule :fetch-dwd-prognosis-data
               (partial dwd/import-dwd-data-into-datomic :prognosis)
               :at "10:30"
               :every :day #_[3 :days]
               :singleton true)

(jobs/schedule :fetch-dwd-measured-data
               (partial dwd/import-dwd-data-into-datomic :measured)
               :at "10:35"
               :every :day
               :singleton true)




