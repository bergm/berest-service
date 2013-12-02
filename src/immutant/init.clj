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




(jobs/schedule :fetch-dwd-climate-data
               #(println "I gonna fetch dwd climate data every day.")
               ;:in [1 :second]
               :every :minute
               ;:repeat 3
               :singleton true) ;just one server has to fetch and store the data

