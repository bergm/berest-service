(ns berest-service.handler
  (:require [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [compojure.core :refer [ANY defroutes context] :as cc]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [liberator.core :refer [resource defresource]]
            [berest-service.berest.datomic :as db]
            [berest-service.rest.farm :as farm]
            [berest-service.rest.home :as home]
            [berest-service.rest.login :as login]
            [berest-service.rest.common :as common]
            [berest-service.rest.weather-station :as wstation]
            [berest-service.rest.api :as api]
            [berest-service.rest.data :as data]
            [berest-service.rest.plot :as plot]
            [berest-service.rest.user :as user]
            [bidi.bidi :as bidi]))

(defresource api
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok #(api/get-api (:request %)))

(defresource simulate
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok #(api/simulate (:request %)))

(defresource calculate
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok #(api/calculate (:request %)))

(defroutes api-routes
  (ANY "/" [] api)
  (ANY "/simulate" [] simulate)
  (ANY "/calculate" [] calculate))

(def api-subroutes
  {"a" api
   "simulate" simulate
   "calculate" calculate})

(defresource users
  :allowed-methods [:post :get]
  :available-media-types ["text/html"]
  :handle-ok #(user/get-users (:request %))
  :post! #(user/create-user (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))

(defresource user
  :allowed-methods [:put :get]
  :available-media-types ["text/html"]
  :handle-ok #(user/get-user (:request %))
  :put! #(user/update-user (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))


(defroutes user-routes
  (ANY "/" [] users)
  (ANY "/:id" [id] (user id)))

(def user-subroutes
  {"" users
   [:id] user})


(defresource weather-stations
  :allowed-methods [:post :get]
  :available-media-types ["text/html"]
  :handle-ok #(wstation/get-weather-stations (:request %))
  :post! #(wstation/create-weather-station (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))

(defresource weather-station [id]
  :allowed-methods [:put :get]
  :available-media-types ["text/html"]
  :handle-ok #(wstation/get-weather-station id (:request %))
  :put! #(wstation/update-weather-station id (:request %)))

(defroutes weather-station-routes
  (ANY "/" [] weather-stations)
  (ANY "/:id" [id] (weather-station id)))

(def weather-station-subroutes
  {"" weather-stations
   [:id] weather-station})





(defresource plots
  :allowed-methods [:post :get]
  :available-media-types ["text/html"]
  :handle-ok #(plot/get-plots (:request %))
  :post! #(plot/create-plot (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))


(defresource plot [farm-id id]
  :allowed-methods [:put :get]
  :available-media-types ["text/html"]
  :handle-ok #(plot/get-plot farm-id id (get-in % [:request :query-params]))
  :put! #(plot/update-plot farm-id id (:request %)))

(def plot-subroutes
  {"" plots
   [:plot-id "/"] plot})





(defresource farms
  :allowed-methods [:post :get]
  :available-media-types ["text/html"]
  :handle-ok #(farm/get-farms (:request %))
  :post! #(farm/create-farm (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))

(defresource farm [id]
  :allowed-methods [:put :get]
  :available-media-types ["text/html"]
  :handle-ok #(farm/get-farm id (:request %))
  :put! #(farm/update-farm id (:request %)))

(defroutes farm-routes
  (ANY "/" [] farms)
  (ANY "/:id" [id] (farm id))
  (context "/:farm-id/plots" [farm-id]
           (ANY "/" [] plots)
           (ANY "/:id" [id] (plot farm-id id))))

(def farm-subroutes
  {"" farms
   [:farm-id] {"/" farm
               "/plots/" plot-subroutes}})






(defresource data
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok #(data/get-data (:request %)))

(defroutes data-routes
  (ANY "/" [] data)
  (context "/users" [] user-routes)
  (context "/weather-stations" [] weather-station-routes)
  (context "/farms" [] farm-routes))


(def data-subroutes
  {"" data
   "users/" user-subroutes
   "weather-stations/" weather-station-subroutes
   "farms/" farm-subroutes})


(defresource home
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok #(home/get-home (:request %)))

(defresource login
  :allowed-methods [:post :get]
  :available-media-types ["text/html"]
  :handle-ok #(login/login-page (:request %))
  :post! (fn [context] nil)
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))

(defresource logout
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok "logout")


(defroutes compojure-service-routes
  (ANY "/" [] home)
  (ANY "/login/" [] login)
  (ANY "/logout/" [] logout)
  (context "/api" [] api-routes)
  (context "/data" [] data-routes)
  (route/resources "/")
  (route/not-found "Not Found"))

(def compojure-rest-service
  (handler/api compojure-service-routes))


(def bidi-service-routes
  ["/" {"" home
        "login/" login
        "logout/" logout
        "api/" api-subroutes
        "data/" data-subroutes}])

#_(bidi/match-route bidi-service-routes "/data/farms/123/plots/345/")
#_(bidi/path-for bidi-service-routes plot :farm-id 123 :plot-id 123)

(def rest-service
  (-> bidi-service-routes
      bidi/make-handler
      (wrap-resource ,,, "public")
      wrap-keyword-params
      wrap-nested-params
      wrap-params))

