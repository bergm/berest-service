(ns berest-service.handler
  (:require [compojure.core :refer [ANY defroutes context] :as cc]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [berest-service.rest
             [farm :as farm]
             [home :as home]
             [login :as login]
             [common :as common]
             [weather-station :as wstation]
             [api :as api]
             [plot :as plot]
             [user :as user]]))

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


(defresource weather-stations
  :allowed-methods [:post :get]
  :available-media-types ["text/html"]
  :handle-ok #(wstation/get-weather-stations (:request %))
  :post! #(wstation/post-weather-stations (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))

(defresource weather-station [id]
  :allowed-methods [:put :get]
  :available-media-types ["text/html"]
  :handle-ok #(wstation/get-weather-station (:request %) id)
  :post! wstation/post-weather-stations
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))

(defroutes weather-station-routes
  (ANY "/" [] weather-stations)
  (ANY "/:id" [id] (weather-station id)))



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
  :put! #(farm/update-farm (:request %)))

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
  :put! #(plot/update-plot (:request %)))

(defroutes farm-routes
  (ANY "/" [] farms)
  (ANY "/:id" [id] (farm id))
  (context "/:farm-id/plots" [farm-id]
           (ANY "/" [] plots)
           (ANY "/:id" [id] (plot farm-id id))))




(defresource data
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok "data")



(defroutes data-routes
  (ANY "/" [] data)
  (context "/users" [] user-routes)
  (context "/weather-station" [] weather-station-routes)
  (context "/farms" [] farm-routes))





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


(defroutes service-routes
  (ANY "/" [] home)
  (ANY "/" [] login)
  (ANY "/" [] logout)
  (context "/api" [] api-routes)
  (context "/data" [] data-routes)
  (route/resources "/")
  (route/not-found "Not Found"))


(def rest-service
  (handler/api service-routes))
