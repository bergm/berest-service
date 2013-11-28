(ns immutant.init
  (:require [immutant.web :as web]
            [io.pedestal.service.http :as http]
            [berest-service.service :as service]))

;for ring handlers
#_(:use [ring.middleware.resource :only [wrap-resource]]
        [ring.util.response :only [redirect]])
#_(defn handler [request]
  (redirect "/index.html"))
#_(immutant.web/start (wrap-resource handler "public"))

;for pedestal service apps
(web/start-servlet "/" (::http/servlet (http/create-servlet service/service)))
