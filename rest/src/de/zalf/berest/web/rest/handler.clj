(ns de.zalf.berest.web.rest.handler
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
            [ring.util.request :as ring-req]
            [liberator.core :refer [resource defresource]]
            [liberator.representation :as liberator]
            [liberator.dev :refer [wrap-trace]]
            [de.zalf.berest.core.datomic :as db]
            [de.zalf.berest.web.rest.farm :as farm]
            [de.zalf.berest.web.rest.farms :as farms]
            [de.zalf.berest.web.rest.crop :as crop]
            [de.zalf.berest.web.rest.crops :as crops]
            [de.zalf.berest.web.rest.soil :as soil]
            [de.zalf.berest.web.rest.home :as home]
            [de.zalf.berest.web.rest.login :as login]
            [de.zalf.berest.web.rest.common :as common]
            [de.zalf.berest.web.rest.weather-station :as wstation]
            [de.zalf.berest.web.rest.api :as api]
            [de.zalf.berest.web.rest.data :as data]
            [de.zalf.berest.web.rest.plot :as plot]
            [de.zalf.berest.web.rest.user :as user]
            [de.zalf.berest.web.rest.users :as users]
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

;;----------------------------------------------------------------------------------

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

;;----------------------------------------------------------------------------------

(defresource auth-api
  (authorized-default-resource :admin :farmer :consultant)
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok #(api/get-auth-api (:request %)))

(defresource auth-simulate
  (authorized-default-resource :admin :farmer :consultant)
  :allowed-methods [:get :options]
  :available-media-types ["text/html" "text/csv" "text/tab-separated-values"
                          "application/edn" "application/json"]
  :handle-ok #(api/auth-simulate (-> % :representation :media-type) (:request %)))

(defresource auth-calculate
  (authorized-default-resource :admin :farmer :consultant)
  :allowed-methods [:get :options]
  :available-media-types ["text/html" "text/csv" "text/tab-separated-values"
                          "application/edn" "application/json"]
  :handle-ok #(api/auth-calculate (-> % :representation :media-type) (:request %)))

(def auth-api-subroutes
  {"" auth-api
   "simulate" auth-simulate
   "calculate" auth-calculate})




;;----------------------------------------------------------------------------------

(defresource
  weather-stations
  (authorized-default-resource :admin :consultant :farmer :guest)
  :allowed-methods [:post :get]
  :available-media-types ["text/html"]
  :handle-ok #(wstation/get-weather-stations (:request %))
  :post! #(wstation/create-weather-station (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))

(defresource weather-station [id]
  (authorized-default-resource :admin :consultant :farmer :guest)
  :allowed-methods [:put :get]
  :available-media-types ["text/html"]
  :handle-ok #(wstation/get-weather-station id (:request %))
  :put! #(wstation/update-weather-station id (:request %)))

(def weather-station-subroutes
  {"" weather-stations
   [:id "/"] weather-station})



;;----------------------------------------------------------------------------------

(defresource plots
  (authorized-default-resource :admin :farmer :consultant)
  :allowed-methods [:post :get :options]
  :available-media-types ["text/html" "application/edn"]
  :handle-ok (fn [{request :request
                   {media-type :media-type} :representation
                   :as context}]
               (condp = media-type
                 "application/edn" (plot/get-plots-edn (-> context :identity :user/id) request)
                 "text/html" (plot/get-plots request)))
  :post! #(plot/create-plot (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))


(defresource plot [farm-id id]
  (authorized-default-resource :admin :consultant :farmer)
  :allowed-methods [:put :get :options]
  :available-media-types ["text/html" "application/edn"]
  :handle-ok #(plot/get-plot farm-id id (get-in % [:request :query-params]))
  :put! #(plot/update-plot farm-id id (:request %)))

(def plot-subroutes
  {"" plots
   [:plot-id "/"] plot})


;;----------------------------------------------------------------------------------


(defresource farms
  (authorized-default-resource :admin :consultant :farmer)
  :allowed-methods [:post :get :options]
  :available-media-types ["text/html" "application/edn"]
  :handle-ok (fn [{request :request
                   {media-type :media-type} :representation
                   :as context}]
               (condp = media-type
                 "application/edn" (farms/get-farms-edn (-> context :identity :user/id) request)
                 "text/html" (farms/get-farms request)))

  :post! #(farm/create-farm (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))

(defresource farm [id]
  (authorized-default-resource :admin :consultant :farmer)
  :allowed-methods [:put :get :options]
  :available-media-types ["text/html"]
  :handle-ok #(farm/get-farm id (:request %))
  :put! #(farm/update-farm id (:request %)))

(def farm-subroutes
  {"" farms
   [:farm-id] {"/" farm
               "/plots/" plot-subroutes}})

;;----------------------------------------------------------------------------------


(defresource users
  (authorized-default-resource :admin)
  :allowed-methods [:options :post :get]
  :available-media-types ["text/html" "application/edn"]
  :handle-ok (fn [{request :request
                   {media-type :media-type} :representation
                   :as context}]
               (condp = media-type
                 "application/edn" (users/get-users-edn request)
                 "text/html" (users/get-users request)))

  :post! #(user/create-user (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))

(defresource user
  ;authorize right now just the :admin role and the user itself for a path with the users-id
  :authorized? #(default-authorized? [:admin] %
                                     :single-user? true
                                     :single-user-id-route-params-key :user-id)

  :allowed-methods [:put :get]
  :available-media-types ["text/html"]
  :handle-ok (fn [{request :request
                   {media-type :media-type} :representation
                   :as context}]
               (let [user-id (-> request :route-params :user-id)]
                 (condp = media-type
                   "application/edn" (user/get-user-edn user-id request)
                   "text/html" (user/get-user user-id request))))

  :put! #(user/update-user (:request %))
  :post-redirect? (fn [ctx] nil #_{:location (format "/postbox/%s" (::id ctx))}))

(def user-subroutes
  {"" users
   [:user-id] {"/" user
               "/weather-stations/" weather-station-subroutes
               "/farms/" farm-subroutes}})


;;----------------------------------------------------------------------------------

(defresource crops
  ;(authorized-default-resource :admin :consultant :farmer)
  :allowed-methods [:get :options]
  :available-media-types ["text/html" "application/edn"]
  :handle-ok (fn [{request :request
                   {media-type :media-type} :representation
                   :as context}]
               (condp = media-type
                 "application/edn" (crops/get-crops-edn request)
                 "text/html" (crops/get-crops request))))

(defresource crop
  ;(authorized-default-resource :admin :consultant :farmer)
  :allowed-methods [:get :options]
  :available-media-types ["text/html" "application/edn"]
  :handle-ok (fn [{request :request
                   {media-type :media-type} :representation
                   :as context}]
               (let [id (-> request :route-params :crop-id)]
                 (condp = media-type
                   "application/edn" (crop/get-crop-edn id request)
                   "text/html" (crop/get-crop id request)))))

(def crop-subroutes
  {"" crops
   [:crop-id] {"/" crop}
               ;"/dc-to-developmental-state-names/" plot-subroutes
               ;"/dc-to-rel-dc-days/"
               ;"/rel-dc-day-to-cover-degrees/"
               ;"/rel-dc-day-to-extraction-depths/"
               ;"/rel-dc-day-to-transpiration-factors/"
               ;"/rel-dc-day-to-quotient-aet-pets/"
               ;}
   })

;;----------------------------------------------------------------------------------

(defresource soils
  ;(authorized-default-resource :admin :consultant :farmer)
  :allowed-methods [:get :options]
  :available-media-types ["text/html" "application/edn"]
  :handle-ok (fn [{request :request
                   {media-type :media-type} :representation
                   :as context}]
               (condp = media-type
                 "application/edn" {:error "not implemented yet"}
                 "text/html" "not implemented yet")))

(defresource soil [id]
  ;(authorized-default-resource :admin :consultant :farmer)
  :allowed-methods [:get :options]
  :available-media-types ["text/html"]
  :handle-ok "not implemented yet")

(def soil-subroutes
  {"" soils
   [:soil-id] {"/" soil}})


;;----------------------------------------------------------------------------------

(defresource data
  #_(authorized-default-resource :admin :consultant :farmer :guest)
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok #(data/get-data (:request %)))

(def data-subroutes
  {"" data
   "users/" user-subroutes
   ;"weather-stations/" weather-station-subroutes
   "crops/" crop-subroutes
   "soils/" soil-subroutes})

;;----------------------------------------------------------------------------------


(defresource home
  :allowed-methods [:get :options]
  :available-media-types ["text/html" "application/edn"]
  :handle-ok (fn [{request :request
                   {media-type :media-type} :representation}]
               (condp = media-type
                 "application/edn" (home/get-home-edn request)
                 "text/html" (home/get-home request))))

(defresource login
  :allowed-methods [:options :post :get]
  :available-media-types ["text/html" "application/edn"]

  :exists? (fn [{{media-type :media-type} :representation}]
             {:rest-client? (= media-type "application/edn")})

  :handle-ok (fn [{request :request
                   {media-type :media-type} :representation}]
               (let [user-id (some-> request :session :identity :user/id)
                     ;full-url (ring-req/request-url request)
                     base-url (str (-> request :scheme name)
                                   "://"
                                   (get-in request [:headers "host"])
                                   "/")]
                 (condp = media-type
                   "application/edn" {:session-token (db/create-session-token user-id)
                                      :user-home {:url (str base-url "data/users/" user-id "/")}}
                   "text/html" (login/get-login request))))

  :post! (fn [{{{user-id :username
                 pwd :password} :params} :request}]
           (let [ident (db/credentials user-id pwd)]
             (assoc-in {} [:request :session :identity] ident)))

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

;;----------------------------------------------------------------------------------

(defresource logout
  :allowed-methods [:post]
  :available-media-types ["text/html"]
  :handle-ok "logout")

;;----------------------------------------------------------------------------------

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
          (ring-resp/header ,,, "X-Dev-Mode" "true")
          #_(#(do (println %) %))))))


(def rest-service
  (let [backend #_(signed-token-backend "abcdefg") (session-backend :unauthorized-handler unauthorized-handler)]
    (-> (bidi/make-handler bidi-service-routes)
        (wrap-resource ,,, "public")
        wrap-access-control-allow-*
        (wrap-authorization ,,, backend)
        (wrap-authentication ,,, backend)
        wrap-keyword-params
        wrap-nested-params
        wrap-edn-params
        wrap-params
        wrap-session
        #_(wrap-trace ,,, :header :ui))))

