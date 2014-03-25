(ns berest-service.handler
  (:require [clojure.set :as set]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.java.io :as cjio]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.edn :refer [wrap-edn-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.util.response :as ring-resp]
            [liberator.core :refer [resource defresource]]
            [liberator.representation :as liberator]
            [liberator.dev :refer [wrap-trace]]
            [berest.datomic :as db]
            [berest-service.rest.farm :as farm]
            [berest-service.rest.home :as home]
            [berest-service.rest.login :as login]
            [berest-service.rest.common :as common]
            [berest-service.rest.weather-station :as wstation]
            [berest-service.rest.api :as api]
            [berest-service.rest.data :as data]
            [berest-service.rest.plot :as plot]
            [berest-service.rest.user :as user]
            [bidi.bidi :as bidi]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.backends.token :refer [signed-token-backend]]
            [buddy.sign.generic :refer [sign unsign]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]))


(defn pp [& xs]
  (println (with-out-str (apply pp/pprint xs))))

(defn- default-authorized?
  [authorized-roles {:keys [request] :as context}
   & {single-user? :single-user?
      single-user-id-route-params-key :single-user-id-route-params-key
      single-user-id-fn :get-single-user-id-fn
      :or {single-user? false
           single-user-id-route-params-key :id
           single-user-id-fn (fn [context id-key]
                               (-> context :request :route-params id-key))}}]
  ;options requests don't need authentication
  (if (= :options (:request-method request))
    true
    (let [authorized-roles* (into #{} authorized-roles)
          auth-token (some-> request :headers (get ,,, "x-auth-token"))
          identity (or (and auth-token
                            (db/check-session-token auth-token))
                       (some-> request :session :identity))
          {user-id :user/id
           user-roles :user/roles} identity
          single-user-id (when single-user?
                           (single-user-id-fn context single-user-id-route-params-key))]
      (if (or (and single-user? user-id single-user-id (= user-id single-user-id))
              (and user-roles (not-empty (set/intersection authorized-roles* user-roles))))
        {:identity identity}
        (throw-unauthorized)))))


(defn authorized-default-resource
  "create a default resource which is just authorized for any of the the given roles (or connected)"
  [& authorized-roles]
  {:authorized? (partial default-authorized? authorized-roles)})


(defresource api
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok #(api/get-api (:request %)))

(defresource simulate
  :allowed-methods [:get]
  :available-media-types ["text/html" "text/csv" "application/edn" "application/json"]
  :handle-ok #(api/simulate (:request %)))

(defresource calculate
  :allowed-methods [:get]
  :available-media-types ["text/html" "text/csv" "application/edn" "application/json"]
  :handle-ok #(api/calculate (:request %)))

(def api-subroutes
  {"" api
   "simulate" simulate
   "calculate" calculate})



(defresource auth-api
  (authorized-default-resource :admin :farmer :consultant)
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok #(api/get-auth-api (:request %)))

(defresource auth-simulate
  (authorized-default-resource :admin :farmer :consultant)
  :allowed-methods [:get]
  :available-media-types ["text/html" "text/csv" "application/edn" "application/json"]
  :handle-ok #(api/auth-simulate (:request %)))

(defresource auth-calculate
  (authorized-default-resource :admin :farmer :consultant)
  :allowed-methods [:get :options]
  :available-media-types ["text/html" "text/csv" "application/edn" "application/json"]
  :handle-ok #(api/auth-calculate (:request %)))

(def auth-api-subroutes
  {"" auth-api
   "simulate" auth-simulate
   "calculate" auth-calculate})



(defresource users
  (authorized-default-resource :admin)
  :allowed-methods [:post :get]
  :available-media-types ["text/html"]
  :handle-ok #(user/get-users (:request %))
  :post! #(user/create-user (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))

(defresource user
  ;authorize right now just the :admin role and the user itself for a path with the users-id
  :authorized? #(default-authorized? [:admin] %
                                     :single-user? true
                                     :single-user-id-route-params-key :id)

  :allowed-methods [:put :get]
  :available-media-types ["text/html"]
  :handle-ok #(user/get-user (:request %))
  :put! #(user/update-user (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))

(def user-subroutes
  {"" users
   [:id] user})




(defresource
  weather-stations
  (authorized-default-resource :admin :consultant :farmer :guest)
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

(def weather-station-subroutes
  {"" weather-stations
   [:id "/"] weather-station})





(defresource plots
  (authorized-default-resource :admin :farmer :consultant)
  :allowed-methods [:post :get :options]
  :available-media-types ["text/html" "application/edn"]
  :handle-ok (fn [{request :request
                   {media-type :media-type} :representation
                   :as context}]
               (condp = media-type
                 "application/edn" (plot/get-plots-edn request)
                 "text/html" (plot/get-plots request)))
  :post! #(plot/create-plot (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))


(defresource plot [farm-id id]
  (authorized-default-resource :admin :consultant :farmer)
  :allowed-methods [:put :get]
  :available-media-types ["text/html"]
  :handle-ok #(plot/get-plot farm-id id (get-in % [:request :query-params]))
  :put! #(plot/update-plot farm-id id (:request %)))

(def plot-subroutes
  {"" plots
   [:plot-id "/"] plot})





(defresource farms
  (authorized-default-resource :admin :consultant :farmer)
  :allowed-methods [:post :get :options]
  :available-media-types ["text/html"]
  :handle-ok (fn [{request :request
                   {media-type :media-type} :representation
                   :as context}]
               (condp = media-type
                 "application/edn" (farm/get-farms-edn (-> context :identity :user/id) request)
                 "text/html" (farm/get-farms request)))

  :post! #(farm/create-farm (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))

(defresource farm [id]
  (authorized-default-resource :admin :consultant :farmer)
  :allowed-methods [:put :get]
  :available-media-types ["text/html"]
  :handle-ok #(farm/get-farm id (:request %))
  :put! #(farm/update-farm id (:request %)))

(def farm-subroutes
  {"" farms
   [:farm-id] {"/" farm
               "/plots/" plot-subroutes}})






(defresource data
  (authorized-default-resource :admin :consultant :farmer :guest)
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok #(data/get-data (:request %)))

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
  :allowed-methods [:options :post :get]
  :available-media-types ["text/html" "application/edn"]

  :exists? (fn [{{media-type :media-type} :representation}]
             {:rest-client? (= media-type "application/edn")})

  :handle-ok (fn [{request :request
                   {media-type :media-type} :representation}]
               (let [user-id (some-> request :session :identity :user/id)]
                 (condp = media-type
                   "application/edn" {:session-token (db/create-session-token user-id)}
                   "text/html" (login/get-login request))))

  :post! (fn [{{{user-id :username
                 pwd :password} :params} :request}]
           (assoc-in {} [:request :session :identity] (db/credentials user-id pwd)))

  :post-redirect? (fn [{:keys [request rest-client?]}]
                    (when-not rest-client?
                      {:location (get-in request [:params :return] "/")}))

  ;set new? to false in order to get to a regular 200 in case of login via REST client
  :new? false
  :respond-with-entity? true

  ;we've got to handle the redirect on our own in order to store the new session data
  :handle-see-other (fn [{:keys [request location]}]
                      (liberator/ring-response {:headers {"Location" location}
                                                :session {:identity (-> request :session :identity)}})))



(defresource logout
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok "logout")


(def bidi-service-routes
  ["/" {"" home
        "login" login
        "logout" logout
        "api/" api-subroutes
        "auth-api/" auth-api-subroutes
        "data/" data-subroutes}])


(defn unauthorized-handler
  [request metadata]
  (println "unauthorized-handler -> ")
  (if (authenticated? request)

    ;; If request is authenticated, raise 403 instead
    ;; of 401 (because user is authenticated but permission
    ;; denied is raised).
    (-> (ring-resp/response "Error authenticated but not authorized")
        (ring-resp/status ,,, 403))

    ;; Else, redirect it to login with link of current url
    ;; for post login redirect user to current url.
    (ring-resp/redirect (str "/login?return=" (:uri request)))))



#_(bidi/match-route bidi-service-routes "/data/farms/123/plots/345/")
#_(bidi/path-for bidi-service-routes plot :farm-id 123 :plot-id 123)


;middleware to wrap responses with Access-Control-Allow-*: * [Origin Headers] fields
(defn wrap-access-control-allow-*
  [handler]
  (fn [request]
    (when-let [response (handler request)]
      (-> response
          (ring-resp/header ,,, "Access-Control-Allow-Origin" "*")
          (ring-resp/header ,,, "Access-Control-Allow-Headers" "origin, x-auth-token, x-csrf-token, content-type, accept")
          #_(#(do (println %) %))))))


(def rest-service
  (let [backend #_(signed-token-backend "abcdefg") (session-backend :unauthorized-handler unauthorized-handler)]
    (-> bidi-service-routes
        bidi/make-handler
        (wrap-resource ,,, "public")
        wrap-access-control-allow-*
        (wrap-authorization ,,, backend)
        (wrap-authentication ,,, backend)
        wrap-keyword-params
        wrap-nested-params
        wrap-edn-params
        wrap-params
        wrap-session
        #_(wrap-trace :header :ui))))

